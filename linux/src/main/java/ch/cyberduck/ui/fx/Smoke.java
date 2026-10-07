package ch.cyberduck.ui.fx;

/*
 * Copyright (c) 2002-2026 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

import ch.cyberduck.core.AttributedList;
import ch.cyberduck.core.Controller;
import ch.cyberduck.core.DisabledListProgressListener;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathCache;
import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.SessionPoolFactory;
import ch.cyberduck.core.nio.LocalProtocol;
import ch.cyberduck.core.pool.SessionPool;
import ch.cyberduck.core.threading.SessionBackgroundAction;
import ch.cyberduck.core.threading.WorkerBackgroundAction;
import ch.cyberduck.core.worker.ListWorker;

import org.apache.commons.lang3.StringUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TableRow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;

/**
 * Scripted scenarios run against the local filesystem without a server. Started with
 * {@code --smoke <mode> <arguments>}. Prints {@code SMOKE OK <mode> ...} and returns 0 on success, otherwise prints
 * {@code SMOKE FAIL} and returns 1. Never runs longer than {@link #TIMEOUT} seconds.
 */
public final class Smoke {

    public static final int TIMEOUT = 120;

    private Smoke() {
        //
    }

    /**
     * @param arguments Mode followed by the arguments of the mode
     * @return True if the mode runs without a window
     */
    public static boolean isHeadless(final List<String> arguments) {
        return !arguments.isEmpty() && "core-list".equals(arguments.get(0));
    }

    /**
     * @param arguments Mode followed by the arguments of the mode
     * @param browser   Window to drive or null for modes without a window
     * @return Process exit code
     */
    public static int run(final List<String> arguments, final BrowserController browser) {
        watchdog();
        try {
            if(arguments.isEmpty()) {
                throw new IllegalArgumentException("Missing smoke test mode");
            }
            final String mode = arguments.get(0);
            switch(mode) {
                case "core-list":
                    System.out.printf("SMOKE OK core-list %d%n", list(arguments.get(1)));
                    return 0;
                case "list":
                    System.out.printf("SMOKE OK list %d%n", list(browser, arguments.get(1)));
                    hold();
                    return 0;
                case "connect":
                    System.out.printf("SMOKE OK connect %d%n", connect(browser, arguments.get(1)));
                    hold();
                    return 0;
                case "connect-fail":
                    System.out.printf("SMOKE OK connect-fail %s%n", connectFail(browser));
                    return 0;
                case "navigate":
                    navigate(browser, arguments.get(1));
                    System.out.println("SMOKE OK navigate");
                    hold();
                    return 0;
                default:
                    throw new IllegalArgumentException(String.format("Unknown smoke test mode %s", mode));
            }
        }
        catch(Exception e) {
            System.out.printf("SMOKE FAIL %s%n", e.getMessage());
            e.printStackTrace(System.out);
            return 1;
        }
    }

    /**
     * Keep the window open for the number of seconds in the environment variable SMOKE_HOLD, for example to take a
     * screenshot.
     */
    private static void hold() throws InterruptedException {
        final String seconds = System.getenv("SMOKE_HOLD");
        if(seconds != null) {
            TimeUnit.SECONDS.sleep(Long.parseLong(seconds));
        }
    }

    private static void watchdog() {
        final Thread thread = new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(TIMEOUT);
                System.out.printf("SMOKE FAIL timeout after %d seconds%n", TIMEOUT);
                Runtime.getRuntime().halt(1);
            }
            catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "smoke-watchdog");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Mount the local filesystem at the directory in the browser window and wait for the table to show it.
     *
     * @return Number of rows in the table
     */
    static int list(final BrowserController browser, final String directory) throws Exception {
        mount(browser, directory);
        return onFx(() -> browser.getTable().getItems().size());
    }

    /**
     * Mount the local filesystem at the directory and wait until the table shows it
     */
    static void mount(final BrowserController browser, final String directory) throws Exception {
        final Host host = new Host(new LocalProtocol(), new LocalProtocol().getDefaultHostname());
        host.setDefaultPath(directory);
        onFx(() -> {
            browser.mount(host);
            return null;
        });
        awaitRendered(browser, directory);
    }

    /**
     * Open the connection dialog, choose the local filesystem, type the folder and press connect
     *
     * @return Number of rows in the table
     */
    static int connect(final BrowserController browser, final String directory) throws Exception {
        onFx(() -> {
            Platform.runLater(browser::connect);
            return null;
        });
        await("connection dialog", () -> onFx(() -> null != browser.getConnectionDialog() && browser.getConnectionDialog().isShowing()));
        onFx(() -> {
            final ConnectionDialog dialog = browser.getConnectionDialog();
            final Protocol local = ProtocolFactory.get().forName("file");
            check("local protocol available", null != local);
            dialog.getProtocolBox().setValue(local);
            dialog.getPathField().setText(directory);
            dialog.getConnectButton().fire();
            return null;
        });
        awaitRendered(browser, directory);
        return onFx(() -> browser.getTable().getItems().size());
    }

    /**
     * Connect to a port nobody listens on. The failure must reach the user as a dialog and leave the browser
     * disconnected, not hang.
     *
     * @return Title of the error dialog
     */
    static String connectFail(final BrowserController browser) throws Exception {
        final Host host = HostBuilder.fromUrl(ProtocolFactory.get(), "sftp://user@127.0.0.1:1/");
        onFx(() -> {
            browser.mount(host);
            return null;
        });
        await("error dialog", () -> onFx(() -> null != dialog()));
        final String title = onFx(() -> dialog().getHeaderText());
        final String message = onFx(() -> dialog().getContentText());
        System.out.printf("Error dialog: %s | %s%n", title, message);
        check("error dialog has a title", StringUtils.isNotBlank(title));
        closeDialog();
        await("disconnected", () -> onFx(() -> !browser.isMounted() && null == browser.getRendered()));
        return title;
    }

    /**
     * Close the dialog that is showing like pressing its close button
     */
    private static void closeDialog() throws Exception {
        onFx(() -> {
            dialog().getScene().getWindow().hide();
            return null;
        });
    }

    /**
     * @return The dialog that is showing or null
     */
    private static DialogPane dialog() {
        for(Window window : Window.getWindows()) {
            if(window.isShowing() && window.getScene() != null && window.getScene().getRoot() instanceof DialogPane pane) {
                return pane;
            }
        }
        return null;
    }

    static void awaitRendered(final BrowserController browser, final String directory) throws Exception {
        await(String.format("directory %s shown", directory), () -> onFx(() -> null != browser.getRendered() && directory.equals(browser.getRendered().getAbsolute())));
    }

    static List<String> names(final BrowserController browser) throws Exception {
        return onFx(() -> browser.getTable().getItems().stream().map(Path::getName).collect(Collectors.toList()));
    }

    /**
     * Send a double click to the table row with the given name, like a user would
     */
    static void doubleClick(final BrowserController browser, final String name) throws Exception {
        await(String.format("row %s", name), () -> onFx(() -> null != row(browser, name)));
        onFx(() -> {
            final TableRow<?> row = row(browser, name);
            Event.fireEvent(row, new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 2,
                false, false, false, false, true, false, false, true, false, false, null));
            return null;
        });
    }

    private static TableRow<?> row(final BrowserController browser, final String name) {
        for(Node node : browser.getTable().lookupAll(".table-row-cell")) {
            if(node instanceof TableRow<?> row && !row.isEmpty() && row.getItem() instanceof Path path && path.getName().equals(name)) {
                return row;
            }
        }
        return null;
    }

    /**
     * Walk down two levels with double clicks, up with the Up button, type a path and go back
     */
    static void navigate(final BrowserController browser, final String directory) throws Exception {
        mount(browser, directory);
        doubleClick(browser, "a");
        awaitRendered(browser, directory + "/a");
        doubleClick(browser, "b");
        awaitRendered(browser, directory + "/a/b");
        check("file.txt shown in a/b", List.of("file.txt").equals(names(browser)));
        onFx(() -> {
            browser.getUpButton().fire();
            return null;
        });
        awaitRendered(browser, directory + "/a");
        onFx(() -> {
            browser.getUpButton().fire();
            return null;
        });
        awaitRendered(browser, directory);
        // Typed path
        onFx(() -> {
            browser.getLocation().setText(directory + "/a/b");
            browser.getLocation().fireEvent(new ActionEvent());
            return null;
        });
        awaitRendered(browser, directory + "/a/b");
        // Back to the directory shown before the typed path
        onFx(() -> {
            browser.getBackButton().fire();
            return null;
        });
        awaitRendered(browser, directory);
        // A directory that cannot be listed leaves the previous one in place
        onFx(() -> {
            browser.getLocation().setText(directory + "/missing");
            browser.getLocation().fireEvent(new ActionEvent());
            return null;
        });
        // The failure is shown to the user and has to be acknowledged
        await("error dialog", () -> onFx(() -> null != dialog()));
        System.out.printf("Error dialog: %s%n", onFx(() -> dialog().getHeaderText()));
        closeDialog();
        await("location restored after failed listing", () -> onFx(() -> directory.equals(browser.getLocation().getText())));
        check("directory still shown after failed listing", directory.equals(onFx(() -> browser.getRendered().getAbsolute())));
    }

    static void check(final String description, final boolean condition) {
        if(!condition) {
            throw new IllegalStateException(String.format("Check failed: %s", description));
        }
    }

    /**
     * Run on the JavaFX application thread and wait for the result
     */
    static <T> T onFx(final Callable<T> callable) throws Exception {
        final FutureTask<T> task = new FutureTask<>(callable);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }

    /**
     * Poll until the condition holds
     */
    static void await(final String description, final Callable<Boolean> condition) throws Exception {
        final long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);
        while(!condition.call()) {
            if(System.currentTimeMillis() > deadline) {
                throw new IllegalStateException(String.format("Timeout waiting for %s", description));
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
    }

    /**
     * @return Number of entries listed through the core for a directory on the local filesystem
     */
    static int list(final String directory) throws Exception {
        final Host host = new Host(new LocalProtocol(), new LocalProtocol().getDefaultHostname());
        final Controller controller = new HeadlessController();
        final SessionPool pool = SessionPoolFactory.create(controller, host);
        try {
            final SessionBackgroundAction<AttributedList<Path>> action = new WorkerBackgroundAction<>(controller, pool,
                new ListWorker(new PathCache(100), new Path(directory, EnumSet.of(Path.Type.directory)), new DisabledListProgressListener()));
            final AttributedList<Path> list = controller.background(action).get();
            if(action.hasFailed()) {
                throw new IllegalStateException(action.getFailure().getMessage(), action.getFailure());
            }
            return list.size();
        }
        finally {
            pool.shutdown();
        }
    }
}
