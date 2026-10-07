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
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.TransferAction;
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
     * @return True if the scenario ends the application itself and the process is expected to exit without being told to
     */
    public static boolean endsApplication(final List<String> arguments) {
        return !arguments.isEmpty() && "windows".equals(arguments.get(0));
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
                case "locale":
                    System.out.printf("SMOKE OK locale refresh=%s%n", onFx(() -> browser.getRefresh().getText()));
                    hold();
                    return 0;
                case "vault":
                    System.out.println(vault(browser, arguments.get(1), arguments.get(2)));
                    return 0;
                case "sync":
                    System.out.println(sync(browser, arguments.get(1), arguments.get(2)));
                    return 0;
                case "duplicate":
                    System.out.println(duplicate(browser, arguments.get(1)));
                    return 0;
                case "info":
                    System.out.println(info(browser, arguments.get(1), arguments.get(2)));
                    return 0;
                case "preferences":
                    preferences(browser);
                    System.out.println("SMOKE OK preferences");
                    return 0;
                case "preferences-check":
                    System.out.printf("SMOKE OK preferences-check timeout=%s%n", preferencesCheck(browser));
                    return 0;
                case "connect":
                    System.out.printf("SMOKE OK connect %d%n", connect(browser, arguments.get(1)));
                    hold();
                    return 0;
                case "download":
                    System.out.printf("SMOKE OK download progress=%d%n", download(browser, arguments.get(1), arguments.get(2)));
                    hold();
                    return 0;
                case "url":
                    System.out.printf("SMOKE OK url %d%n", url(browser, arguments.get(1)));
                    hold();
                    return 0;
                case "tls":
                    tls(browser, arguments.get(1));
                    System.out.println("SMOKE OK tls");
                    hold();
                    return 0;
                case "sftp":
                    sftp(browser, arguments.get(1), arguments.get(2), arguments.get(3), arguments.get(4), arguments.get(5));
                    System.out.println("SMOKE OK sftp");
                    hold();
                    return 0;
                case "windows":
                    windows(browser, arguments.get(1), arguments.get(2));
                    System.out.println("SMOKE OK windows");
                    // Quit like a user and leave the process to end by itself
                    onFx(() -> {
                        browser.getMenuBar().getMenus().get(0).getItems().stream().filter(i -> "Quit".equals(i.getText())).findFirst().orElseThrow().fire();
                        return null;
                    });
                    return 0;
                case "fileops":
                    fileops(browser, arguments.get(1));
                    System.out.println("SMOKE OK fileops");
                    hold();
                    return 0;
                case "upload":
                    System.out.printf("SMOKE OK upload rows=%d%n", upload(browser, arguments.get(1), arguments.get(2)));
                    hold();
                    return 0;
                case "bookmarks":
                    bookmarks(browser, arguments.get(1));
                    System.out.println("SMOKE OK bookmarks");
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
     * Download <code>f.bin</code> from the source folder. Then replace the local copy and download again: the user is
     * asked what to do with the existing file and chooses to overwrite it.
     *
     * @return Number of progress notifications received for the transfers
     */
    static int download(final BrowserController browser, final String source, final String target) throws Exception {
        mount(browser, source);
        PreferencesFactory.get().setProperty("queue.download.folder", target);
        final java.nio.file.Path original = java.nio.file.Paths.get(source, "f.bin");
        final java.nio.file.Path copy = java.nio.file.Paths.get(target, "f.bin");
        final TransferController transfers = TransferController.get();
        onFx(() -> {
            transfers.show();
            return null;
        });

        // Slow enough to see the progress. A megabyte takes about four seconds.
        PreferencesFactory.get().setProperty("queue.download.bandwidth.bytes", "262144");
        select(browser, "f.bin");
        onFx(() -> {
            browser.getDownloadButton().fire();
            return null;
        });
        await("transfer listed", () -> onFx(() -> 1 == transfers.getTable().getItems().size()));
        await("progress shown", () -> onFx(() -> {
            final double fraction = transfers.fraction(transfers.getTable().getItems().get(0));
            return fraction > 0d && fraction < 1d;
        }));
        System.out.printf("While running: %s%n", onFx(() -> transfers.status(transfers.getTable().getItems().get(0))));
        await("first download", () -> transfers.getCompleted() >= 1);
        check("downloaded content is the same", -1 == java.nio.file.Files.mismatch(original, copy));
        await("status complete", () -> onFx(() -> "Complete".equals(transfers.status(transfers.getTable().getItems().get(0)))));
        final int events = transfers.getProgressEvents();
        check("progress was reported", events >= 1);

        // The local file now differs, so the second download has to ask
        PreferencesFactory.get().setProperty("queue.download.bandwidth.bytes", "-1");
        java.nio.file.Files.write(copy, "old local content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        select(browser, "f.bin");
        onFx(() -> {
            browser.getDownloadButton().fire();
            return null;
        });
        chooseAction(TransferAction.overwrite, () -> check("old content is still there while asking",
            "old local content".equals(new String(java.nio.file.Files.readAllBytes(copy), java.nio.charset.StandardCharsets.UTF_8))));
        await("second download", () -> transfers.getCompleted() >= 2);
        check("overwritten with the remote content", -1 == java.nio.file.Files.mismatch(original, copy));

        // Stop a slow transfer, then resume it
        PreferencesFactory.get().setProperty("queue.download.bandwidth.bytes", "65536");
        java.nio.file.Files.delete(copy);
        select(browser, "f.bin");
        onFx(() -> {
            browser.getDownloadButton().fire();
            return null;
        });
        await("third transfer listed", () -> onFx(() -> 3 == transfers.getTable().getItems().size()));
        final Transfer slow = onFx(() -> transfers.getTable().getItems().get(2));
        await("third transfer running", () -> onFx(() -> slow.isRunning() && transfers.fraction(slow) > 0.05d));
        onFx(() -> {
            transfers.getTable().getSelectionModel().clearSelection();
            transfers.getTable().getSelectionModel().select(slow);
            transfers.stop();
            return null;
        });
        await("stopped", () -> onFx(() -> !slow.isRunning()));
        await("status incomplete", () -> onFx(() -> "Incomplete".equals(transfers.status(slow))));
        check("only part was downloaded", java.nio.file.Files.size(copy) < java.nio.file.Files.size(original));
        final int completed = transfers.getCompleted();
        onFx(() -> {
            slow.setBandwidth(-1f);
            transfers.resume();
            return null;
        });
        await("resumed transfer", () -> transfers.getCompleted() >= completed + 1);
        check("resumed content is complete", -1 == java.nio.file.Files.mismatch(original, copy));

        // Remove from the list
        onFx(() -> {
            transfers.getTable().getSelectionModel().selectAll();
            transfers.remove();
            return null;
        });
        await("list emptied", () -> onFx(() -> transfers.getTable().getItems().isEmpty()));
        return events;
    }

    /**
     * Open a URL like the desktop does when it hands one over, first one that is valid and then one that is not
     *
     * @return Number of rows in the table
     */
    static int url(final BrowserController browser, final String directory) throws Exception {
        onFx(() -> {
            browser.open(String.format("file://%s", directory));
            return null;
        });
        awaitRendered(browser, directory);
        final int rows = onFx(() -> browser.getTable().getItems().size());
        onFx(() -> {
            Platform.runLater(() -> browser.open("sftp://"));
            return null;
        });
        await("error dialog for the invalid URL", () -> onFx(() -> null != dialog()));
        check("names the problem", "Invalid URL".equals(onFx(() -> dialog().getHeaderText())));
        closeDialog();
        return rows;
    }

    /**
     * Connect over HTTPS to a server with a certificate that nobody trusts. The certificate is shown. Continuing lets the
     * connection proceed, and the failure of the server that does not speak WebDAV reaches the user as an error dialog.
     * Cancelling stops the connection without further messages.
     */
    static void tls(final BrowserController browser, final String port) throws Exception {
        for(boolean trust : new boolean[]{true, false}) {
            final Host host = HostBuilder.fromUrl(ProtocolFactory.get(), String.format("davs://user:secret@localhost:%s/", port));
            onFx(() -> {
                browser.mount(host);
                return null;
            });
            await("certificate dialog", () -> onFx(() -> null != dialog()));
            final String header = onFx(() -> dialog().getHeaderText());
            final String content = onFx(() -> dialog().getContentText());
            System.out.printf("Certificate dialog: %s | %s%n", header, content);
            check("names the server", header.contains("localhost"));
            check("shows the subject", content.contains("CN=localhost"));
            check("shows the fingerprint", content.contains("SHA-256 fingerprint: "));
            onFx(() -> {
                final DialogPane pane = dialog();
                // Continue is the first button, Cancel the second
                ((javafx.scene.control.Button) pane.lookupButton(pane.getButtonTypes().get(trust ? 0 : 1))).fire();
                return null;
            });
            if(trust) {
                // The connection goes on and fails because the server does not speak WebDAV. The user is told.
                await("error dialog", () -> onFx(() -> null != dialog()));
                final String failure = onFx(() -> dialog().getHeaderText());
                System.out.printf("After continuing: %s%n", failure);
                check("failure is explained", StringUtils.isNotBlank(failure));
                closeDialog();
            }
            else {
                // Declining is the decision of the user, so there is nothing to report
                await("disconnected after cancelling", () -> onFx(() -> !browser.isMounted()));
                check("no error dialog after the user cancelled", null == onFx(Smoke::dialog));
                System.out.println("After cancelling: disconnected without an error dialog");
            }
            await("disconnected", () -> onFx(() -> !browser.isMounted() && null == browser.getRendered()));
        }
    }

    /**
     * Wait for the condition while answering the dialogs a user would answer: allow the unknown host key and type the
     * password. Any other dialog fails the scenario.
     *
     * @param counts Number of times each kind of dialog was answered
     */
    static void awaitAnswering(final String description, final String password, final java.util.Map<String, Integer> counts,
                               final Callable<Boolean> condition) throws Exception {
        final long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);
        while(!condition.call()) {
            if(System.currentTimeMillis() > deadline) {
                throw new IllegalStateException(String.format("Timeout waiting for %s", description));
            }
            final String kind = onFx(() -> {
                final DialogPane pane = dialog();
                if(null == pane) {
                    return null;
                }
                final String header = StringUtils.defaultString(pane.getHeaderText());
                if(pane.lookup(".password-field") != null) {
                    ((javafx.scene.control.PasswordField) pane.lookup(".password-field")).setText(password);
                    ((javafx.scene.control.Button) pane.lookupButton(pane.getButtonTypes().get(0))).fire();
                    return "password";
                }
                if(header.contains("fingerprint")) {
                    ((javafx.scene.control.Button) pane.lookupButton(pane.getButtonTypes().get(0))).fire();
                    return "hostkey";
                }
                throw new IllegalStateException(String.format("Unexpected dialog while waiting for %s: %s | %s", description, header, pane.getContentText()));
            });
            if(kind != null) {
                counts.merge(kind, 1, Integer::sum);
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
    }

    /**
     * The checklist of the usable minimum against a real SFTP server: add a bookmark, connect with host key and password
     * prompts, browse, upload, download, rename, create and delete, disconnect and connect again.
     *
     * @param workdir Folder on this computer for the files to upload and download
     */
    static void sftp(final BrowserController browser, final String host, final String port, final String user, final String password, final String workdir) throws Exception {
        final java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        final java.nio.file.Path work = java.nio.file.Paths.get(workdir);
        final BookmarkController bookmarks = browser.getBookmarks();
        final TransferController transfers = TransferController.get();

        // Save a bookmark. It stores the username but not the password.
        onFx(() -> {
            Platform.runLater(bookmarks::add);
            return null;
        });
        await("bookmark dialog", () -> onFx(() -> null != bookmarks.getDialog() && bookmarks.getDialog().isShowing()));
        onFx(() -> {
            final ConnectionDialog dialog = bookmarks.getDialog();
            dialog.getNicknameField().setText("SFTP test");
            dialog.getProtocolBox().setValue(ProtocolFactory.get().forName("sftp"));
            dialog.getServerField().setText(host);
            dialog.getPortField().setText(port);
            dialog.getUsernameField().setText(user);
            dialog.getConnectButton().fire();
            return null;
        });
        await("bookmark saved", () -> onFx(() -> 1 == bookmarks.getList().getItems().size()));
        final Host bookmark = onFx(() -> bookmarks.getList().getItems().get(0));

        // Open it: the unknown host key is shown, then the password is asked
        onFx(() -> {
            bookmarks.getList().getSelectionModel().select(bookmark);
            bookmarks.connect();
            return null;
        });
        awaitAnswering("listing after connecting", password, counts, () -> onFx(() -> null != browser.getRendered()));
        check("host key was shown", counts.getOrDefault("hostkey", 0) == 1);
        check("password was asked", counts.getOrDefault("password", 0) == 1);
        check("upload folder is listed", names(browser).contains("upload"));

        // Navigate into the folder that can be written to
        doubleClick(browser, "upload");
        awaitAnswering("upload folder", password, counts, () -> onFx(() -> null != browser.getRendered() && browser.getRendered().getAbsolute().endsWith("/upload")));

        // Upload
        final java.nio.file.Path up = work.resolve("up.bin");
        java.nio.file.Files.write(up, new byte[512 * 1024]);
        final int completed = transfers.getCompleted();
        onFx(() -> {
            browser.upload(List.of(up.toFile()));
            return null;
        });
        awaitAnswering("upload", password, counts, () -> transfers.getCompleted() >= completed + 1);
        awaitAnswering("uploaded file shown", password, counts, () -> names(browser).contains("up.bin"));

        // Download it again to a different folder
        final java.nio.file.Path down = java.nio.file.Files.createDirectories(work.resolve("down"));
        PreferencesFactory.get().setProperty("queue.download.folder", down.toString());
        select(browser, "up.bin");
        onFx(() -> {
            browser.getDownloadButton().fire();
            return null;
        });
        awaitAnswering("download", password, counts, () -> transfers.getCompleted() >= completed + 2);
        check("downloaded file is the same", -1 == java.nio.file.Files.mismatch(up, down.resolve("up.bin")));

        // Rename, create a folder, delete both. Rename needs a second connection on a stateful protocol.
        select(browser, "up.bin");
        onFx(() -> {
            Platform.runLater(browser::rename);
            return null;
        });
        answerInput("renamed.bin");
        awaitAnswering("renamed file shown", password, counts, () -> names(browser).contains("renamed.bin") && !names(browser).contains("up.bin"));
        onFx(() -> {
            Platform.runLater(browser::newFolder);
            return null;
        });
        answerInput("folder");
        awaitAnswering("folder shown", password, counts, () -> names(browser).contains("folder"));
        for(String name : List.of("folder", "renamed.bin")) {
            select(browser, name);
            onFx(() -> {
                Platform.runLater(browser::delete);
                return null;
            });
            await("confirmation", () -> onFx(() -> null != dialog()));
            onFx(() -> {
                final DialogPane pane = dialog();
                ((javafx.scene.control.Button) pane.lookupButton(pane.getButtonTypes().get(0))).fire();
                return null;
            });
            awaitAnswering(String.format("%s removed", name), password, counts, () -> !names(browser).contains(name));
        }
        check("folder is empty again", names(browser).isEmpty());
        System.out.printf("Dialogs answered: %s%n", counts);

        // Disconnect and open the bookmark again. The host key is known now, the password is asked again.
        menu(browser, "Disconnect");
        await("disconnected", () -> onFx(() -> !browser.isMounted()));
        final int hostkeys = counts.getOrDefault("hostkey", 0);
        onFx(() -> {
            bookmarks.getList().getSelectionModel().select(bookmark);
            bookmarks.connect();
            return null;
        });
        awaitAnswering("listing after connecting again", password, counts, () -> onFx(() -> null != browser.getRendered()));
        check("known host key is not asked again", hostkeys == counts.getOrDefault("hostkey", 0));
        menu(browser, "Disconnect");
        await("disconnected again", () -> onFx(() -> !browser.isMounted()));
    }

    private static void menu(final BrowserController browser, final String name) throws Exception {
        onFx(() -> {
            browser.getMenuBar().getMenus().get(0).getItems().stream().filter(i -> name.equals(i.getText())).findFirst().orElseThrow().fire();
            return null;
        });
    }

    /**
     * Two windows each with its own connection, then close one like the window button does
     */
    static void windows(final BrowserController first, final String one, final String two) throws Exception {
        final MainController main = MainController.get();
        mount(first, one);
        check("one window", 1 == onFx(() -> main.getBrowsers().size()));
        menu(first, "New Browser");
        await("second window", () -> onFx(() -> 2 == main.getBrowsers().size()));
        final BrowserController second = onFx(() -> main.getBrowsers().get(1));
        check("second window is disconnected", !onFx(second::isMounted));
        mount(second, two);
        check("windows show their own folders", !names(first).equals(names(second)));
        check("first still connected", onFx(first::isMounted) && one.equals(onFx(() -> first.getRendered().getAbsolute())));

        // The window button disconnects first and then closes the window
        onFx(() -> {
            second.getStage().fireEvent(new javafx.stage.WindowEvent(second.getStage(), javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
            return null;
        });
        await("second window closed", () -> onFx(() -> 1 == main.getBrowsers().size() && !second.getStage().isShowing()));
        check("second disconnected", !onFx(second::isMounted));
        check("first window unaffected", onFx(first::isMounted) && first.getStage().isShowing());
    }

    /**
     * Create a folder, rename it, delete a file and the folder, each through its dialog
     */
    static void fileops(final BrowserController browser, final String directory) throws Exception {
        final java.nio.file.Path root = java.nio.file.Paths.get(directory);
        mount(browser, directory);
        check("starts empty", names(browser).isEmpty());

        // New folder
        onFx(() -> {
            Platform.runLater(browser::newFolder);
            return null;
        });
        answerInput("n");
        await("folder shown", () -> names(browser).contains("n"));
        check("folder exists", java.nio.file.Files.isDirectory(root.resolve("n")));
        check("new folder is selected", "n".equals(onFx(() -> browser.getTable().getSelectionModel().getSelectedItem().getName())));

        // Rename it
        onFx(() -> {
            Platform.runLater(browser::rename);
            return null;
        });
        answerInput("m");
        await("renamed shown", () -> names(browser).contains("m") && !names(browser).contains("n"));
        check("renamed on disk", java.nio.file.Files.isDirectory(root.resolve("m")) && !java.nio.file.Files.exists(root.resolve("n")));
        check("renamed is selected", "m".equals(onFx(() -> browser.getTable().getSelectionModel().getSelectedItem().getName())));

        // A file that appears behind the back of the browser is shown after reload
        java.nio.file.Files.write(root.resolve("file.txt"), "text".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        onFx(() -> {
            browser.reload();
            return null;
        });
        await("file shown after reload", () -> names(browser).contains("file.txt"));

        // Delete after confirmation
        for(String name : List.of("file.txt", "m")) {
            select(browser, name);
            onFx(() -> {
                Platform.runLater(browser::delete);
                return null;
            });
            await("confirmation", () -> onFx(() -> null != dialog()));
            check("confirmation names the item", onFx(() -> dialog().getHeaderText()).contains(name));
            check("not deleted before confirming", java.nio.file.Files.exists(root.resolve(name)));
            onFx(() -> {
                final DialogPane pane = dialog();
                ((javafx.scene.control.Button) pane.lookupButton(pane.getButtonTypes().get(0))).fire();
                return null;
            });
            await(String.format("%s removed", name), () -> !names(browser).contains(name));
            check("deleted on disk", !java.nio.file.Files.exists(root.resolve(name)));
        }
        check("empty again", names(browser).isEmpty());
    }

    /**
     * Type the text in the dialog that asks for a line of text and press OK
     */
    static void answerInput(final String text) throws Exception {
        await("input dialog", () -> onFx(() -> null != dialog() && null != dialog().lookup(".text-field")));
        onFx(() -> {
            final DialogPane pane = dialog();
            ((javafx.scene.control.TextField) pane.lookup(".text-field")).setText(text);
            ((javafx.scene.control.Button) pane.lookupButton(javafx.scene.control.ButtonType.OK)).fire();
            return null;
        });
    }

    interface Check {
        void run() throws Exception;
    }

    /**
     * Wait for the dialog that asks what to do with an existing file, run the check while it is open, choose the
     * action and continue
     */
    static void chooseAction(final TransferAction action, final Check whileAsking) throws Exception {
        await("file exists dialog", () -> onFx(() -> null != dialog()));
        check("asks about the existing file", "File exists".equals(onFx(() -> dialog().getHeaderText())));
        whileAsking.run();
        onFx(() -> {
            final DialogPane pane = dialog();
            @SuppressWarnings("unchecked") final javafx.scene.control.ComboBox<TransferAction> choices = (javafx.scene.control.ComboBox<TransferAction>) pane.lookup(".combo-box");
            choices.setValue(action);
            ((javafx.scene.control.Button) pane.lookupButton(pane.getButtonTypes().get(0))).fire();
            return null;
        });
    }

    /**
     * Upload <code>g.bin</code> from the source folder into the folder that is shown. Then change the remote file and
     * upload again: the user is asked what to do with the existing file and chooses to overwrite it.
     *
     * @return Number of rows shown after the first upload
     */
    static int upload(final BrowserController browser, final String source, final String target) throws Exception {
        mount(browser, target);
        final java.io.File local = new java.io.File(source, "g.bin");
        final java.nio.file.Path remote = java.nio.file.Paths.get(target, "g.bin");
        final TransferController transfers = TransferController.get();

        final int before = transfers.getCompleted();
        onFx(() -> {
            browser.upload(List.of(local));
            return null;
        });
        await("first upload", () -> transfers.getCompleted() >= before + 1);
        check("uploaded content is the same", -1 == java.nio.file.Files.mismatch(local.toPath(), remote));
        // The folder is listed again after the transfer
        await("uploaded file shown", () -> names(browser).contains("g.bin"));
        final int rows = names(browser).size();

        java.nio.file.Files.write(remote, "old remote content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        onFx(() -> {
            browser.upload(List.of(local));
            return null;
        });
        chooseAction(TransferAction.overwrite, () -> check("old content is still there while asking",
            "old remote content".equals(new String(java.nio.file.Files.readAllBytes(remote), java.nio.charset.StandardCharsets.UTF_8))));
        await("second upload", () -> transfers.getCompleted() >= before + 2);
        check("overwritten with the local content", -1 == java.nio.file.Files.mismatch(local.toPath(), remote));
        return rows;
    }

    /**
     * Select the row with the name in the table
     */
    static void select(final BrowserController browser, final String name) throws Exception {
        onFx(() -> {
            final Path file = browser.getTable().getItems().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow(() -> new IllegalStateException(String.format("No row %s", name)));
            browser.getTable().getSelectionModel().clearSelection();
            browser.getTable().getSelectionModel().select(file);
            return null;
        });
    }

    /**
     * Create, open, change and delete a bookmark through the bookmark pane and check the file in the home folder.
     * Run with an empty home folder, as the smoke script does.
     */
    static void bookmarks(final BrowserController browser, final String directory) throws Exception {
        final BookmarkController controller = browser.getBookmarks();
        check("no bookmarks to start with", 0 == onFx(() -> controller.getList().getItems().size()));
        final java.nio.file.Path folder = java.nio.file.Paths.get(LinuxApplicationPreferences.userHome(), ".duck", "Bookmarks");

        // Add
        onFx(() -> {
            Platform.runLater(controller::add);
            return null;
        });
        await("bookmark dialog", () -> onFx(() -> null != controller.getDialog() && controller.getDialog().isShowing()));
        onFx(() -> {
            final ConnectionDialog dialog = controller.getDialog();
            dialog.getNicknameField().setText("Local test");
            dialog.getProtocolBox().setValue(ProtocolFactory.get().forName("file"));
            dialog.getPathField().setText(directory);
            dialog.getConnectButton().fire();
            return null;
        });
        await("bookmark listed", () -> onFx(() -> 1 == controller.getList().getItems().size()));
        final Host bookmark = onFx(() -> controller.getList().getItems().get(0));
        check("name", "Local test".equals(bookmark.getNickname()));
        final java.nio.file.Path file = folder.resolve(String.format("%s.duck", bookmark.getUuid()));
        await("bookmark file written", () -> java.nio.file.Files.exists(file));

        // Open with a double click on the row
        await("bookmark row", () -> onFx(() -> null != bookmarkRow(controller, bookmark)));
        onFx(() -> {
            final javafx.scene.control.ListCell<?> cell = bookmarkRow(controller, bookmark);
            controller.getList().getSelectionModel().select(bookmark);
            Event.fireEvent(cell, new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 2,
                false, false, false, false, true, false, false, true, false, false, null));
            return null;
        });
        awaitRendered(browser, directory);

        // Edit
        onFx(() -> {
            controller.getList().getSelectionModel().select(bookmark);
            Platform.runLater(controller::edit);
            return null;
        });
        await("edit dialog", () -> onFx(() -> null != controller.getDialog() && controller.getDialog().isShowing()));
        check("edit starts with the saved name", "Local test".equals(onFx(() -> controller.getDialog().getNicknameField().getText())));
        onFx(() -> {
            controller.getDialog().getNicknameField().setText("Renamed");
            controller.getDialog().getConnectButton().fire();
            return null;
        });
        await("name saved", () -> new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8).contains("Renamed"));
        check("same bookmark", bookmark.getUuid().equals(onFx(() -> controller.getList().getItems().get(0).getUuid())));

        hold();

        // Delete after confirmation
        onFx(() -> {
            controller.getList().getSelectionModel().select(bookmark);
            Platform.runLater(controller::delete);
            return null;
        });
        await("confirmation", () -> onFx(() -> null != dialog()));
        onFx(() -> {
            final DialogPane pane = dialog();
            ((javafx.scene.control.Button) pane.lookupButton(pane.getButtonTypes().get(0))).fire();
            return null;
        });
        await("bookmark removed", () -> onFx(() -> controller.getList().getItems().isEmpty()));
        await("bookmark file removed", () -> !java.nio.file.Files.exists(file));
    }

    private static javafx.scene.control.ListCell<?> bookmarkRow(final BookmarkController controller, final Host bookmark) {
        for(Node node : controller.getList().lookupAll(".list-cell")) {
            if(node instanceof javafx.scene.control.ListCell<?> cell && !cell.isEmpty() && bookmark.equals(cell.getItem())) {
                return cell;
            }
        }
        return null;
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

    /**
     * Open the preferences window from the menu like a user and wait until it is showing
     */
    private static PreferencesController openPreferences(final BrowserController browser) throws Exception {
        onFx(() -> {
            browser.getMenuBar().getMenus().get(0).getItems().stream().filter(i -> "Preferences…".equals(i.getText())).findFirst().orElseThrow().fire();
            return null;
        });
        await("preferences window", () -> onFx(() -> null != PreferencesController.get().getStage() && PreferencesController.get().getStage().isShowing()));
        return PreferencesController.get();
    }

    /**
     * Change values in the preferences window. They are saved when changed.
     */
    private static void preferences(final BrowserController browser) throws Exception {
        final PreferencesController window = openPreferences(browser);
        onFx(() -> {
            window.getTimeout().getValueFactory().setValue(45);
            window.getRetries().getValueFactory().setValue(2);
            window.getStage().close();
            return null;
        });
    }

    /**
     * @return Timeout shown in the preferences window after a restart
     */
    private static int preferencesCheck(final BrowserController browser) throws Exception {
        final PreferencesController window = openPreferences(browser);
        return onFx(() -> {
            final int timeout = window.getTimeout().getValue();
            window.getStage().close();
            return timeout;
        });
    }

    /**
     * Open the properties of a file from the menu like a user. The size comes from the listing and the rest is read in
     * the background.
     *
     * @return The line to print
     */
    private static String info(final BrowserController browser, final String directory, final String name) throws Exception {
        mount(browser, directory);
        select(browser, name);
        onFx(() -> {
            browser.getMenuBar().getMenus().get(0).getItems().stream().filter(i -> "Get Info".equals(i.getText())).findFirst().orElseThrow().fire();
            return null;
        });
        await("info window", () -> onFx(() -> !browser.getInfos().isEmpty() && null != browser.getInfos().get(0).getStage() && browser.getInfos().get(0).getStage().isShowing()));
        final InfoController info = browser.getInfos().get(0);
        await("permissions shown", () -> onFx(() -> !info.getPermissions().getText().isEmpty()));
        // The address is read from the session, which can take a moment
        await("url shown", () -> onFx(() -> !info.getUrl().getText().isEmpty()));
        final String line = onFx(() -> String.format("SMOKE OK info size=%d permissions=%s url=%s", info.getBytes(), info.getPermissions().getText(), info.getUrl().getText()));
        onFx(() -> {
            info.getStage().close();
            return null;
        });
        return line;
    }

    /**
     * An encrypted vault with the dialogs a user would use: create it, unlock it, upload a file into it, see the real
     * name in the listing and look at what is on disk. Then lock it.
     *
     * @param directory Empty folder for the vault
     * @param source    File to upload, with a name that must not appear on disk
     * @return The line to print
     */
    private static String vault(final BrowserController browser, final String directory, final String source) throws Exception {
        final String passphrase = "correct horse battery staple";
        final java.io.File local = new java.io.File(source);
        mount(browser, directory);

        // The dialog does not accept two different passphrases
        onFx(() -> {
            Platform.runLater(browser::createVault);
            return null;
        });
        await("vault dialog", () -> onFx(() -> null != browser.getVaultDialog() && browser.getVaultDialog().isShowing()));
        final boolean refused = onFx(() -> {
            final VaultDialog dialog = browser.getVaultDialog();
            dialog.getName().setText("secret");
            dialog.getPassphrase().setText(passphrase);
            dialog.getConfirm().setText("something else");
            return dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().get(0)).isDisabled();
        });
        check("different passphrases are refused", refused);
        onFx(() -> {
            final VaultDialog dialog = browser.getVaultDialog();
            dialog.getConfirm().setText(passphrase);
            dialog.getSave().setSelected(false);
            final javafx.scene.Node create = dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().get(0));
            check("equal passphrases are accepted", !create.isDisabled());
            ((javafx.scene.control.Button) create).fire();
            return null;
        });
        await("vault folder shown", () -> names(browser).contains("secret"));
        final java.nio.file.Path raw = java.nio.file.Paths.get(directory, "secret");
        check("vault folder has files", java.nio.file.Files.isDirectory(raw) && java.nio.file.Files.list(raw).findAny().isPresent());

        // Unlock with the passphrase typed into the prompt
        select(browser, "secret");
        check("locked vault offers to unlock", "Unlock Vault".equals(onFx(() -> browser.getMenuBar().getMenus().get(0).getItems().stream()
            .filter(i -> i.getText() != null && i.getText().contains("Vault") && !i.getText().contains("Create")).findFirst().orElseThrow().getText())));
        onFx(() -> {
            Platform.runLater(browser::lockUnlockVault);
            return null;
        });
        final java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        awaitAnswering("vault unlocked", passphrase, counts, () -> onFx(() -> browser.isUnlocked(browser.getTable().getSelectionModel().getSelectedItem())));
        check("asked for the passphrase", counts.containsKey("password"));

        // Inside the vault the upload is encrypted
        doubleClick(browser, "secret");
        awaitRendered(browser, directory + "/secret");
        final int before = TransferController.get().getCompleted();
        onFx(() -> {
            browser.upload(List.of(local));
            return null;
        });
        // The transfer opens its own connection, which asks for the passphrase again unless it was saved
        awaitAnswering("upload into vault", passphrase, counts, () -> TransferController.get().getCompleted() >= before + 1);
        await("name shown", () -> names(browser).contains(local.getName()));
        final String names;
        try(java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(raw)) {
            names = walk.map(p -> raw.relativize(p).toString()).collect(java.util.stream.Collectors.joining(","));
        }
        check("the name is not on disk: " + names, !names.contains(local.getName().replaceAll("\\..*$", "")));
        final byte[] plain = java.nio.file.Files.readAllBytes(local.toPath());
        try(java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(raw)) {
            for(java.nio.file.Path file : walk.filter(java.nio.file.Files::isRegularFile).collect(java.util.stream.Collectors.toList())) {
                check("the content is not on disk in " + file, !new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1)
                    .contains(new String(plain, java.nio.charset.StandardCharsets.ISO_8859_1).trim()));
            }
        }

        // Lock again
        onFx(() -> {
            browser.getUpButton().fire();
            return null;
        });
        awaitRendered(browser, directory);
        select(browser, "secret");
        check("unlocked vault offers to lock", "Lock Vault".equals(onFx(() -> browser.getMenuBar().getMenus().get(0).getItems().stream()
            .filter(i -> i.getText() != null && i.getText().contains("Vault") && !i.getText().contains("Create")).findFirst().orElseThrow().getText())));
        onFx(() -> {
            Platform.runLater(browser::lockUnlockVault);
            return null;
        });
        await("vault locked", () -> onFx(() -> !browser.isUnlocked(browser.getTable().getSelectionModel().getSelectedItem())));
        return String.format("SMOKE OK vault listed=%s ondisk=encrypted", local.getName());
    }

    /**
     * Make a copy of a file on the server with the menu command and the dialog that asks for the name
     *
     * @param directory Folder with a file f.txt
     */
    private static String duplicate(final BrowserController browser, final String directory) throws Exception {
        mount(browser, directory);
        final java.nio.file.Path original = java.nio.file.Paths.get(directory, "f.txt");
        select(browser, "f.txt");
        onFx(() -> {
            Platform.runLater(browser::duplicate);
            return null;
        });
        answerInput("f copy.txt");
        await("copy shown", () -> names(browser).contains("f copy.txt"));
        final java.nio.file.Path copy = java.nio.file.Paths.get(directory, "f copy.txt");
        check("the copy has the same content", -1 == java.nio.file.Files.mismatch(original, copy));
        check("the original is still there", java.nio.file.Files.exists(original));
        check("the copy is selected", "f copy.txt".equals(onFx(() -> browser.getTable().getSelectionModel().getSelectedItem().getName())));
        return String.format("SMOKE OK duplicate files=%d", names(browser).size());
    }

    /**
     * Synchronize a folder on the server with a folder on this computer in both directions. Each has a file that the
     * other lacks and a file with the same name that is newer on this computer.
     *
     * @param remote Folder on the server, with r.txt and both.txt
     * @param local  Folder on this computer, with l.txt and a newer both.txt
     */
    private static String sync(final BrowserController browser, final String remote, final String local) throws Exception {
        mount(browser, remote);
        onFx(() -> {
            Platform.runLater(() -> browser.synchronize(new java.io.File(local)));
            return null;
        });
        await("synchronize dialog", () -> onFx(() -> null != dialog()));
        check("asks how to synchronize", "Synchronize".equals(onFx(() -> dialog().getHeaderText())));
        onFx(() -> {
            final DialogPane pane = dialog();
            @SuppressWarnings("unchecked") final javafx.scene.control.ComboBox<TransferAction> choices = (javafx.scene.control.ComboBox<TransferAction>) pane.lookup(".combo-box");
            check("offers download, upload and both", List.of(TransferAction.download, TransferAction.upload, TransferAction.mirror).equals(choices.getItems()));
            choices.setValue(TransferAction.mirror);
            ((javafx.scene.control.Button) pane.lookupButton(pane.getButtonTypes().get(0))).fire();
            return null;
        });
        final java.nio.file.Path remoteRoot = java.nio.file.Paths.get(remote);
        final java.nio.file.Path localRoot = java.nio.file.Paths.get(local);
        await("both folders have all files", () -> java.nio.file.Files.exists(remoteRoot.resolve("l.txt")) && java.nio.file.Files.exists(localRoot.resolve("r.txt")));
        check("l.txt arrived on the server", "from local".equals(new String(java.nio.file.Files.readAllBytes(remoteRoot.resolve("l.txt")), java.nio.charset.StandardCharsets.UTF_8).trim()));
        check("r.txt arrived on this computer", "from server".equals(new String(java.nio.file.Files.readAllBytes(localRoot.resolve("r.txt")), java.nio.charset.StandardCharsets.UTF_8).trim()));
        await("the newer file wins", () -> -1 == java.nio.file.Files.mismatch(remoteRoot.resolve("both.txt"), localRoot.resolve("both.txt")));
        check("the newer content is on both sides", "newer".equals(new String(java.nio.file.Files.readAllBytes(remoteRoot.resolve("both.txt")), java.nio.charset.StandardCharsets.UTF_8).trim()));
        return String.format("SMOKE OK sync remote=%d local=%d", java.nio.file.Files.list(remoteRoot).count(), java.nio.file.Files.list(localRoot).count());
    }
}
