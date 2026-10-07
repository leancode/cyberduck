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
import ch.cyberduck.core.SessionPoolFactory;
import ch.cyberduck.core.nio.LocalProtocol;
import ch.cyberduck.core.pool.SessionPool;
import ch.cyberduck.core.threading.SessionBackgroundAction;
import ch.cyberduck.core.threading.WorkerBackgroundAction;
import ch.cyberduck.core.worker.ListWorker;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

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
        final Host host = new Host(new LocalProtocol(), new LocalProtocol().getDefaultHostname());
        host.setDefaultPath(directory);
        onFx(() -> {
            browser.mount(host);
            return null;
        });
        await("directory listed", () -> onFx(() -> null != browser.getRendered() && directory.equals(browser.getRendered().getAbsolute())));
        return onFx(() -> browser.getTable().getItems().size());
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
