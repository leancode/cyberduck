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
                case "dragout":
                    System.out.println(dragOut(browser, arguments.get(1)));
                    return 0;
                case "ftp":
                    System.out.println(ftp(browser, arguments.get(1), arguments.get(2), arguments.get(3), arguments.get(4), arguments.get(5)));
                    return 0;
                case "compare":
                    System.out.println(compare(browser, arguments.get(1), arguments.get(2), arguments.get(3), arguments.get(4)));
                    return 0;
                case "edit":
                    System.out.println(edit(browser, arguments.get(1), arguments.get(2), arguments.get(3)));
                    return 0;
                case "context":
                    System.out.println(context(browser, arguments.get(1)));
                    return 0;
                case "chmod":
                    System.out.println(chmod(browser, arguments.get(1), arguments.get(2)));
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
                case "sshkey":
                    System.out.printf("SMOKE OK sshkey %s%n", sshkey(browser, arguments.get(1), arguments.get(2), arguments.get(3), arguments.get(4),
                        arguments.size() > 5 ? arguments.get(5) : null));
                    return 0;
                case "files":
                    System.out.println(files(browser, arguments.get(1)));
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
        // The status of a running transfer has the size, the percentage, the speed and the time that is left
        await("speed shown", () -> onFx(() -> transfers.status(slow).contains("/sec") && transfers.status(slow).contains("%")));
        System.out.printf("Status of a running transfer: %s%n", onFx(() -> transfers.status(slow)));
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
                if(header.contains("Unsecured")) {
                    ((javafx.scene.control.Button) pane.lookupButton(pane.getButtonTypes().get(0))).fire();
                    return "unsecured";
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
     * Log in to an SFTP server with a private key chosen in the connection dialog. No password may be asked.
     *
     * @return Summary of the dialogs answered
     */
    private static String sshkey(final BrowserController browser, final String host, final String port, final String user, final String key,
                                 final String terminal) throws Exception {
        final java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        onFx(() -> {
            Platform.runLater(browser::connect);
            return null;
        });
        await("connection dialog", () -> onFx(() -> null != browser.getConnectionDialog() && browser.getConnectionDialog().isShowing()));
        onFx(() -> {
            final ConnectionDialog dialog = browser.getConnectionDialog();
            dialog.getProtocolBox().setValue(ProtocolFactory.get().forName("sftp"));
            check("key field shown for SFTP", dialog.getPrivateKeyField().isVisible() && dialog.getChooseKeyButton().isVisible());
            dialog.getServerField().setText(host);
            dialog.getPortField().setText(port);
            dialog.getUsernameField().setText(user);
            dialog.getPrivateKeyField().setText(key);
            dialog.getConnectButton().fire();
            return null;
        });
        awaitAnswering("login with the key", "", counts, () -> onFx(() -> null != browser.getRendered()));
        check("no password was asked", 0 == counts.getOrDefault("password", 0));
        check("the folder of the account is listed", names(browser).contains("upload"));
        if(terminal != null) {
            // Open in Terminal starts the chosen program with ssh, the port, the key and the folder. The program is a script that writes down its arguments.
            ch.cyberduck.core.preferences.PreferencesFactory.get().setProperty(TerminalLauncher.PROPERTY, terminal);
            onFx(() -> {
                browser.openTerminal();
                return null;
            });
            final java.nio.file.Path log = java.nio.file.Paths.get(terminal + ".log");
            await("terminal started", () -> java.nio.file.Files.exists(log) && content(log).contains("ssh"));
            final String arguments = content(log);
            check("terminal got the port", arguments.contains("-p " + port));
            check("terminal got the key", arguments.contains("-i " + key));
            check("terminal got the account", arguments.contains(user + "@" + host));
            check("terminal starts in the folder", arguments.contains("cd '"));
        }
        menu(browser, "Disconnect");
        await("disconnected", () -> onFx(() -> !browser.isMounted()));
        return counts.toString();
    }

    /**
     * New files, copy, cut and paste, download to a folder and with a name, the address on the clipboard, the size of a
     * folder, and files dragged onto a folder of the listing with the mouse
     *
     * @param directory Folder with f.txt, the folders d, t and big (with a.bin of 10 bytes and b.bin of 5 bytes)
     */
    private static String files(final BrowserController browser, final String directory) throws Exception {
        mount(browser, directory);
        final java.nio.file.Path root = java.nio.file.Paths.get(directory);
        // New File
        onFx(() -> {
            Platform.runLater(browser::newFile);
            return null;
        });
        answerInput("new.txt");
        await("new file shown", () -> names(browser).contains("new.txt"));
        check("new file is empty on disk", 0 == java.nio.file.Files.size(root.resolve("new.txt")));
        check("new file is selected", "new.txt".equals(onFx(() -> browser.getTable().getSelectionModel().getSelectedItem().getName())));
        // Copy and paste next to the original, twice, and into a folder
        select(browser, "f.txt");
        onFx(() -> {
            browser.copyFiles();
            browser.paste();
            return null;
        });
        await("first copy", () -> names(browser).contains("f copy.txt"));
        onFx(() -> {
            browser.paste();
            return null;
        });
        await("second copy", () -> names(browser).contains("f copy 2.txt"));
        check("the copy has the content", -1 == java.nio.file.Files.mismatch(root.resolve("f.txt"), root.resolve("f copy.txt")));
        select(browser, "t");
        onFx(() -> {
            browser.paste();
            return null;
        });
        await("copy in the folder", () -> java.nio.file.Files.exists(root.resolve("t").resolve("f.txt")));
        check("the original stays after a copy", java.nio.file.Files.exists(root.resolve("f.txt")));
        // Cut and paste moves
        select(browser, "new.txt");
        onFx(() -> {
            browser.cutFiles();
            return null;
        });
        select(browser, "d");
        onFx(() -> {
            browser.paste();
            return null;
        });
        await("moved into the folder", () -> java.nio.file.Files.exists(root.resolve("d").resolve("new.txt")));
        await("gone from the folder", () -> !names(browser).contains("new.txt"));
        check("the moved file is gone from the old place", !java.nio.file.Files.exists(root.resolve("new.txt")));
        // Download to a folder and with a name
        final java.nio.file.Path downloads = java.nio.file.Files.createTempDirectory("files-download");
        final Path remote = onFx(() -> browser.getTable().getItems().stream().filter(p -> p.getName().equals("f.txt")).findFirst().orElseThrow());
        onFx(() -> {
            browser.downloadTo(List.of(remote), downloads.toFile());
            return null;
        });
        await("download to the folder", () -> java.nio.file.Files.exists(downloads.resolve("f.txt")));
        onFx(() -> {
            browser.downloadAs(remote, downloads.resolve("renamed.txt").toFile());
            return null;
        });
        await("download with a name", () -> java.nio.file.Files.exists(downloads.resolve("renamed.txt")));
        check("downloads have the content", -1 == java.nio.file.Files.mismatch(root.resolve("f.txt"), downloads.resolve("renamed.txt")));
        // The address on the clipboard
        select(browser, "f.txt");
        onFx(() -> {
            javafx.scene.input.Clipboard.getSystemClipboard().clear();
            browser.copyUrl();
            return null;
        });
        await("address on the clipboard", () -> onFx(() -> {
            final String text = javafx.scene.input.Clipboard.getSystemClipboard().getString();
            return null != text && text.contains("f.txt");
        }));
        // The size of a folder is added up in the info window
        select(browser, "big");
        onFx(() -> {
            Platform.runLater(browser::info);
            return null;
        });
        await("info window", () -> onFx(() -> !browser.getInfos().isEmpty() && browser.getInfos().get(0).getStage().isShowing()));
        final InfoController info = browser.getInfos().get(0);
        check("the size of a folder is not guessed", onFx(() -> info.getSize().getText().isEmpty()));
        onFx(() -> {
            info.getCalculate().fire();
            return null;
        });
        await("folder size calculated", () -> onFx(() -> info.getBytes() == 15));
        onFx(() -> {
            info.getStage().close();
            return null;
        });
        // Drag a file onto a folder of the listing with the mouse: it moves and nothing is downloaded
        String dragged = "skipped";
        if(onPath("xdotool")) {
            final int transfersBefore = onFx(() -> TransferController.get().getTable().getItems().size());
            await("rows", () -> onFx(() -> null != row(browser, "f copy 2.txt") && null != row(browser, "t")));
            final double[] from = onFx(() -> {
                final javafx.geometry.Bounds b = row(browser, "f copy 2.txt").localToScreen(row(browser, "f copy 2.txt").getBoundsInLocal());
                return new double[]{b.getMinX() + 60, b.getMinY() + b.getHeight() / 2};
            });
            final double[] to = onFx(() -> {
                final javafx.geometry.Bounds b = row(browser, "t").localToScreen(row(browser, "t").getBoundsInLocal());
                return new double[]{b.getMinX() + 60, b.getMinY() + b.getHeight() / 2};
            });
            xdotool("mousemove", String.valueOf((int) from[0]), String.valueOf((int) from[1]));
            xdotool("mousedown", "1");
            TimeUnit.MILLISECONDS.sleep(300);
            for(int step = 1; step <= 20; step++) {
                xdotool("mousemove", String.valueOf((int) (from[0] + (to[0] - from[0]) * step / 20)), String.valueOf((int) (from[1] + (to[1] - from[1]) * step / 20)));
                TimeUnit.MILLISECONDS.sleep(50);
            }
            xdotool("mouseup", "1");
            await("moved by the mouse", () -> java.nio.file.Files.exists(root.resolve("t").resolve("f copy 2.txt")));
            await("gone from the old place", () -> !names(browser).contains("f copy 2.txt"));
            check("the file is not on disk at the old place", !java.nio.file.Files.exists(root.resolve("f copy 2.txt")));
            check("nothing was downloaded", transfersBefore == onFx(() -> TransferController.get().getTable().getItems().size()));
            dragged = "ok";
        }
        return String.format("SMOKE OK files copied=ok moved=ok download=ok size=15 dragged=%s", dragged);
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

        // Compare: an unchanged file is skipped, a file that is older than the one on the server is fetched again
        PreferencesFactory.get().setProperty("queue.download.action", TransferAction.comparison.name());
        final java.nio.file.Path copy = down.resolve("up.bin");
        final java.nio.file.attribute.FileTime remoteTime = java.nio.file.Files.getLastModifiedTime(copy);
        final byte[] marker = new byte[512 * 1024];
        marker[0] = 1;
        java.nio.file.Files.write(copy, marker);
        java.nio.file.Files.setLastModifiedTime(copy, remoteTime);
        select(browser, "up.bin");
        onFx(() -> {
            browser.getDownloadButton().fire();
            return null;
        });
        awaitAnswering("compare, same size and time", password, counts, () -> transfers.getCompleted() >= completed + 3);
        check("a file with the same size and time is skipped", java.util.Arrays.equals(marker, java.nio.file.Files.readAllBytes(copy)));
        java.nio.file.Files.setLastModifiedTime(copy, java.nio.file.attribute.FileTime.fromMillis(remoteTime.toMillis() - 3600_000L));
        onFx(() -> {
            browser.getDownloadButton().fire();
            return null;
        });
        awaitAnswering("compare, older local file", password, counts, () -> transfers.getCompleted() >= completed + 4);
        check("an older file is downloaded again", -1 == java.nio.file.Files.mismatch(up, copy));
        PreferencesFactory.get().setProperty("queue.download.action", TransferAction.callback.name());

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

        // Quick Connect takes a URL without a password and asks for it like any other connection
        onFx(() -> {
            browser.getQuick().setText(String.format("sftp://%s@%s:%s/upload", user, host, port));
            browser.getQuick().fireEvent(new ActionEvent());
            return null;
        });
        awaitAnswering("quick connect", password, counts, () -> onFx(() -> null != browser.getRendered()));
        check("quick connect shows the folder of the URL", onFx(() -> "/upload".equals(browser.getRendered().getAbsolute())));
        check("quick connect clears the field", onFx(() -> browser.getQuick().getText().isEmpty()));
        menu(browser, "Disconnect");
        await("disconnected after quick connect", () -> onFx(() -> !browser.isMounted()));
    }

    private static void menu(final BrowserController browser, final String name) throws Exception {
        menu(browser, "File", name);
    }

    private static void menu(final BrowserController browser, final String menu, final String name) throws Exception {
        onFx(() -> {
            final javafx.scene.control.MenuItem item = browser.getMenuBar().getMenus().stream().filter(m -> menu.equals(m.getText())).findFirst().orElseThrow()
                .getItems().stream().filter(i -> name.equals(i.getText())).findFirst().orElseThrow();
            // A click toggles a check item before it fires
            if(item instanceof javafx.scene.control.CheckMenuItem check) {
                check.setSelected(!check.isSelected());
            }
            item.fire();
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

        // Duplicate: a copy with a name and an identity of its own
        onFx(() -> {
            controller.getList().getSelectionModel().select(bookmark);
            controller.duplicate();
            return null;
        });
        await("copy listed", () -> onFx(() -> 2 == controller.getList().getItems().size()));
        final Host copy = onFx(() -> controller.getList().getItems().stream().filter(h -> !h.getUuid().equals(bookmark.getUuid())).findFirst().orElseThrow());
        check("copy is named after the original", "Renamed copy".equals(copy.getNickname()));
        await("copy file written", () -> java.nio.file.Files.exists(folder.resolve(String.format("%s.duck", copy.getUuid()))));

        // Import: hosts of an ssh configuration become bookmarks once
        final java.nio.file.Path config = java.nio.file.Files.createTempFile("ssh-config", "");
        java.nio.file.Files.write(config, "Host Aardvark\n    HostName 127.0.0.1\n    User tester\n    Port 2200\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        final List<Host> found = BookmarkImport.sshConfig(ch.cyberduck.core.LocalFactory.get(config.toString()), ProtocolFactory.get());
        check("one host in the configuration", 1 == found.size());
        check("imported once", 1 == onFx(() -> controller.importHosts(found)));
        check("not imported twice", 0 == onFx(() -> controller.importHosts(BookmarkImport.sshConfigHosts(config))));
        await("imported listed", () -> onFx(() -> 3 == controller.getList().getItems().size()));
        final List<String> unsorted = onFx(() -> controller.getList().getItems().stream().map(Host::getNickname).collect(Collectors.toList()));
        check("the order is the saved order: " + unsorted, "Aardvark".equals(unsorted.get(unsorted.size() - 1)));
        // Sort by name puts it first, and the choice is kept
        onFx(() -> {
            controller.sort("nickname");
            return null;
        });
        check("sorted by name", "Aardvark".equals(onFx(() -> controller.getList().getItems().get(0).getNickname())));
        check("the sort order is saved", "nickname".equals(ch.cyberduck.core.preferences.PreferencesFactory.get().getProperty(BookmarkController.SORT)));
        ch.cyberduck.core.preferences.PreferencesFactory.get().deleteProperty(BookmarkController.SORT);
        final Host imported = onFx(() -> controller.getList().getItems().get(0));
        check("imported server", "127.0.0.1".equals(imported.getHostname()) && 2200 == imported.getPort() && "tester".equals(imported.getCredentials().getUsername()));
        ch.cyberduck.core.BookmarkCollection.defaultCollection().remove(copy);
        ch.cyberduck.core.BookmarkCollection.defaultCollection().remove(imported);
        await("extra bookmarks removed", () -> onFx(() -> 1 == controller.getList().getItems().size()));

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

    /**
     * Open a folder with a double click. A listing that is still being refreshed, for example after unlocking a vault,
     * replaces the folder that was asked for, so the click is repeated.
     */
    static void openFolder(final BrowserController browser, final String name, final String directory) throws Exception {
        for(int attempt = 0; attempt < 3; attempt++) {
            doubleClick(browser, name);
            final long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(15);
            while(System.currentTimeMillis() < deadline) {
                if(onFx(() -> null != browser.getRendered() && directory.equals(browser.getRendered().getAbsolute()))) {
                    return;
                }
                TimeUnit.MILLISECONDS.sleep(100);
            }
        }
        awaitRendered(browser, directory);
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
        // Forward returns to the directory that back left, and back returns again
        check("forward is enabled after back", !onFx(() -> browser.getForwardButton().isDisabled()));
        onFx(() -> {
            browser.getForwardButton().fire();
            return null;
        });
        awaitRendered(browser, directory + "/a/b");
        onFx(() -> {
            browser.getBackButton().fire();
            return null;
        });
        awaitRendered(browser, directory);
        // The search field shows the files with the text in the name
        check("all files shown", names(browser).containsAll(List.of("a", "alpha.txt", "beta.txt")));
        onFx(() -> {
            browser.getSearch().setText("ALP");
            return null;
        });
        check("search shows only alpha.txt", List.of("alpha.txt").equals(names(browser)));
        check("status counts the search", onFx(() -> browser.getStatusText().contains("1 of ")));
        onFx(() -> {
            browser.getSearch().setText("");
            return null;
        });
        check("search cleared", names(browser).contains("beta.txt"));
        // Hidden files are shown with the View menu and hidden again with the same item
        check("hidden file not shown", !names(browser).contains(".hidden"));
        menu(browser, "View", "Show Hidden Files");
        await("hidden file shown", () -> names(browser).contains(".hidden"));
        menu(browser, "View", "Show Hidden Files");
        await("hidden file hidden again", () -> !names(browser).contains(".hidden"));
        // Backspace goes to the parent folder
        doubleClick(browser, "a");
        awaitRendered(browser, directory + "/a");
        onFx(() -> {
            browser.getTable().fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                javafx.scene.input.KeyCode.BACK_SPACE, false, false, false, false));
            return null;
        });
        awaitRendered(browser, directory);
        // The About window names the program
        onFx(() -> {
            Platform.runLater(browser::about);
            return null;
        });
        await("about window", () -> onFx(() -> null != dialog()));
        check("about names the program", onFx(() -> dialog().getHeaderText().startsWith("Cyberduck")));
        closeDialog();
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
            // Speed limits, the level of the log and the language
            window.getDownloadSpeed().getSelectionModel().select(3);
            window.getUploadSpeed().getSelectionModel().select(5);
            window.getLogLevel().getSelectionModel().select(1);
            check("languages are offered", window.getLanguage().getItems().size() > 2);
            window.getLanguage().getSelectionModel().select(1);
            check("a language is saved", !window.getLanguage().getValue().getValue().isEmpty());
            window.getLanguage().getSelectionModel().select(0);
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
        openFolder(browser, "secret", directory + "/secret");
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
        // A file exists before it is complete, so wait for the content
        await("l.txt arrived on the server", () -> content(remoteRoot.resolve("l.txt")).equals("from local"));
        await("r.txt arrived on this computer", () -> content(localRoot.resolve("r.txt")).equals("from server"));
        await("the newer content is on the server", () -> content(remoteRoot.resolve("both.txt")).equals("newer"));
        check("the newer content is still on this computer", content(localRoot.resolve("both.txt")).equals("newer"));
        return String.format("SMOKE OK sync remote=%d local=%d", java.nio.file.Files.list(remoteRoot).count(), java.nio.file.Files.list(localRoot).count());
    }

    /**
     * @return Text of the file without the line break at the end or an empty text while the file is missing
     */
    private static String content(final java.nio.file.Path file) throws java.io.IOException {
        return java.nio.file.Files.exists(file) ? new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8).trim() : "";
    }

    /**
     * Change the permissions of a file with the boxes and the octal number of the info window, and of a folder with
     * what it contains
     *
     * @param directory Folder with the file
     * @param name      File with mode 640 and a folder d with a file inside
     */
    private static String chmod(final BrowserController browser, final String directory, final String name) throws Exception {
        mount(browser, directory);
        final java.nio.file.Path file = java.nio.file.Paths.get(directory, name);
        select(browser, name);
        onFx(() -> {
            browser.getMenuBar().getMenus().get(0).getItems().stream().filter(i -> "Get Info".equals(i.getText())).findFirst().orElseThrow().fire();
            return null;
        });
        await("info window", () -> onFx(() -> !browser.getInfos().isEmpty() && browser.getInfos().get(0).getStage().isShowing()));
        final InfoController info = browser.getInfos().get(0);
        await("permissions shown", () -> onFx(() -> "640".equals(info.getOctal().getText())));
        check("the window can change them", !onFx(() -> info.getApply().isDisabled()));
        // With the boxes: take away what the group may read
        onFx(() -> {
            info.getBit(1, 0).fire();
            return null;
        });
        check("the boxes changed the number", "600".equals(onFx(() -> info.getOctal().getText())));
        onFx(() -> {
            info.getApply().fire();
            return null;
        });
        await("mode 600 on disk", () -> "rw-------".equals(java.nio.file.attribute.PosixFilePermissions.toString(java.nio.file.Files.getPosixFilePermissions(file))));
        // With the number
        onFx(() -> {
            info.getOctal().setText("664");
            info.getApply().fire();
            return null;
        });
        await("mode 664 on disk", () -> "rw-rw-r--".equals(java.nio.file.attribute.PosixFilePermissions.toString(java.nio.file.Files.getPosixFilePermissions(file))));
        onFx(() -> {
            info.getStage().close();
            return null;
        });

        // A folder and what is in it
        final java.nio.file.Path folder = java.nio.file.Paths.get(directory, "d");
        final java.nio.file.Path inside = folder.resolve("inside.txt");
        select(browser, "d");
        onFx(() -> {
            browser.getMenuBar().getMenus().get(0).getItems().stream().filter(i -> "Get Info".equals(i.getText())).findFirst().orElseThrow().fire();
            return null;
        });
        await("second info window", () -> onFx(() -> browser.getInfos().size() == 2 && browser.getInfos().get(1).getStage().isShowing()));
        final InfoController second = browser.getInfos().get(1);
        check("the option for enclosed items is there for a folder", onFx(() -> second.getRecursive().isVisible()));
        onFx(() -> {
            second.getOctal().setText("700");
            second.getRecursive().setSelected(true);
            second.getApply().fire();
            return null;
        });
        await("folder and file inside changed", () -> "rwx------".equals(java.nio.file.attribute.PosixFilePermissions.toString(java.nio.file.Files.getPosixFilePermissions(folder)))
            && "rwx------".equals(java.nio.file.attribute.PosixFilePermissions.toString(java.nio.file.Files.getPosixFilePermissions(inside))));
        onFx(() -> {
            second.getStage().close();
            return null;
        });
        return "SMOKE OK chmod boxes=600 octal=664 recursive=700";
    }

    private static void rightClick(final javafx.scene.Node target) {
        Event.fireEvent(target, new javafx.scene.input.ContextMenuEvent(javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED,
            5, 5, 300, 300, false, null));
    }

    private static List<String> texts(final javafx.scene.control.ContextMenu menu) {
        return menu.getItems().stream().filter(i -> i.getText() != null && i.isVisible()).map(javafx.scene.control.MenuItem::getText)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * The menus of the right mouse button: on a file, on a folder and on the empty area of the listing
     *
     * @param directory Folder with a file f.txt and a folder sub
     */
    private static String context(final BrowserController browser, final String directory) throws Exception {
        mount(browser, directory);
        await("rows", () -> onFx(() -> null != row(browser, "f.txt") && null != row(browser, "sub")));

        // A file: the row is selected and the menu offers what can be done with the file
        onFx(() -> {
            rightClick(row(browser, "f.txt"));
            return null;
        });
        await("menu on file", () -> onFx(() -> browser.getRowMenu().isShowing()));
        check("the row was selected", "f.txt".equals(onFx(() -> browser.getTable().getSelectionModel().getSelectedItem().getName())));
        final List<String> file = onFx(() -> texts(browser.getRowMenu()));
        for(String expected : List.of("Download", "Get Info", "Rename", "Delete", "New Folder", "Upload…", "Refresh")) {
            check(String.format("file menu has %s in %s", expected, file), file.contains(expected));
        }
        check("a file cannot be locked as a vault", file.stream().noneMatch(t -> t.contains("Vault")));
        // The command of the menu is the same as the one of the window menu
        onFx(() -> {
            browser.getRowMenu().getItems().stream().filter(i -> "Get Info".equals(i.getText())).findFirst().orElseThrow().fire();
            return null;
        });
        await("info from the menu", () -> onFx(() -> !browser.getInfos().isEmpty() && browser.getInfos().get(0).getStage().isShowing()));
        onFx(() -> {
            browser.getInfos().get(0).getStage().close();
            return null;
        });

        // A folder can be a vault
        onFx(() -> {
            rightClick(row(browser, "sub"));
            return null;
        });
        await("menu on folder", () -> onFx(() -> "sub".equals(browser.getTable().getSelectionModel().getSelectedItem().getName()) && browser.getRowMenu().isShowing()));
        final List<String> folder = onFx(() -> texts(browser.getRowMenu()));
        check(String.format("folder menu offers the vault in %s", folder), folder.contains("Unlock Vault"));
        check("folder menu can open it", folder.contains("Open"));

        // The empty area of the listing
        final javafx.scene.Node empty = onFx(() -> {
            for(javafx.scene.Node node : browser.getTable().lookupAll(".table-row-cell")) {
                if(node instanceof TableRow<?> r && r.isEmpty()) {
                    return node;
                }
            }
            return null;
        });
        check("there is an empty row to click", empty != null);
        onFx(() -> {
            rightClick(empty);
            return null;
        });
        await("menu on empty area", () -> onFx(() -> browser.getEmptyMenu().isShowing() && !browser.getRowMenu().isShowing()));
        final List<String> blank = onFx(() -> texts(browser.getEmptyMenu()));
        check(String.format("empty area offers the upload picker in %s", blank), blank.contains("Upload…") && blank.contains("New Folder"));
        onFx(() -> {
            Platform.runLater(() -> browser.getEmptyMenu().getItems().stream().filter(i -> "New Folder".equals(i.getText())).findFirst().orElseThrow().fire());
            return null;
        });
        answerInput("created");
        await("folder from the menu", () -> names(browser).contains("created"));
        check("the folder is on disk", java.nio.file.Files.isDirectory(java.nio.file.Paths.get(directory, "created")));
        return String.format("SMOKE OK context file=%d folder=%d empty=%d", file.size(), folder.size(), blank.size());
    }

    /**
     * Edit files with the editor that is set for their type. The editors are scripts that wait a moment, as a person
     * does, and then change the file that they are given. The change has to arrive on the server.
     *
     * @param directory Folder with a.txt, b.md and c.dat that all contain "original"
     * @param textEditor Script for txt files
     * @param markdownEditor Script for md files
     */
    private static String edit(final BrowserController browser, final String directory, final String textEditor, final String markdownEditor) throws Exception {
        final org.apache.logging.log4j.Logger log = org.apache.logging.log4j.LogManager.getLogger(Smoke.class);
        PreferencesFactory.get().setProperty("linux.editor.txt", textEditor);
        PreferencesFactory.get().setProperty("linux.editor.md", markdownEditor);
        // Without a program for the type the default editor is used
        PreferencesFactory.get().setProperty("editor.bundleIdentifier", markdownEditor);
        mount(browser, directory);
        final java.nio.file.Path a = java.nio.file.Paths.get(directory, "a.txt");
        final java.nio.file.Path b = java.nio.file.Paths.get(directory, "b.md");
        final java.nio.file.Path c = java.nio.file.Paths.get(directory, "c.dat");

        select(browser, "a.txt");
        onFx(() -> {
            browser.getMenuBar().getMenus().get(0).getItems().stream().filter(i -> "Edit".equals(i.getText())).findFirst().orElseThrow().fire();
            return null;
        });
        await("text editor changed the file on the server", () -> content(a).equals("original by text"));

        select(browser, "b.md");
        onFx(() -> {
            browser.edit();
            return null;
        });
        await("markdown editor changed the file on the server", () -> content(b).equals("original by markdown"));

        // The menu of the right mouse button names the programs for the file and the choice is used
        select(browser, "a.txt");
        final ch.cyberduck.core.local.Application chosen = new LinuxApplication(markdownEditor, "Markdown editor");
        onFx(() -> {
            browser.edit(chosen, browser.getTable().getSelectionModel().getSelectedItem());
            return null;
        });
        await("chosen editor changed the file", () -> content(a).equals("original by text by markdown"));

        // A type without a program of its own opens in the default editor
        select(browser, "c.dat");
        onFx(() -> {
            browser.edit();
            return null;
        });
        await("default editor changed the file", () -> content(c).equals("original by markdown"));
        log.debug("Edited files");
        return "SMOKE OK edit text=ok markdown=ok chosen=ok default=ok";
    }

    /**
     * Compare a file on the server with the one in the folder for downloads in the program for comparing. The server
     * file is downloaded again as a copy of its own and the local file stays as it is. With the setting to compare files
     * that exist, a download does this for the files that exist and downloads the others.
     *
     * @param remote    Folder on the server with f.txt ("remote content") and g.txt
     * @param downloads Folder for downloads with f.txt ("local content")
     * @param tool      Program for comparing that appends its two arguments to the log, one per line
     * @param log       File with what the program was given
     */
    private static String compare(final BrowserController browser, final String remote, final String downloads, final String tool, final String log) throws Exception {
        PreferencesFactory.get().setProperty("queue.download.folder", downloads);
        PreferencesFactory.get().setProperty(CompareTools.PROPERTY, tool);
        mount(browser, remote);
        final java.nio.file.Path local = java.nio.file.Paths.get(downloads, "f.txt");
        final java.nio.file.Path logged = java.nio.file.Paths.get(log);

        select(browser, "f.txt");
        onFx(() -> {
            browser.compare();
            return null;
        });
        await("program started", () -> java.nio.file.Files.exists(logged) && java.nio.file.Files.readAllLines(logged).size() >= 2);
        List<String> lines = java.nio.file.Files.readAllLines(logged);
        check("the program got the file on this computer first: " + lines, local.toString().equals(lines.get(0)));
        check("and a copy of the server file second: " + lines, lines.get(1).endsWith("/f (server).txt"));
        check("the copy has the server content", "remote content".equals(content(java.nio.file.Paths.get(lines.get(1)))));
        check("the file on this computer was not touched", "local content".equals(content(local)));

        // With the setting, a download compares what exists and downloads the rest
        PreferencesFactory.get().setProperty("linux.download.compare", true);
        onFx(() -> {
            browser.getTable().getSelectionModel().clearSelection();
            for(int i = 0; i < browser.getTable().getItems().size(); i++) {
                final Path item = browser.getTable().getItems().get(i);
                if(item.getName().equals("f.txt") || item.getName().equals("g.txt")) {
                    browser.getTable().getSelectionModel().select(i);
                }
            }
            browser.getDownloadButton().fire();
            return null;
        });
        await("g.txt downloaded", () -> content(java.nio.file.Paths.get(downloads, "g.txt")).equals("remote g"));
        await("program started again", () -> java.nio.file.Files.readAllLines(logged).size() >= 4);
        lines = java.nio.file.Files.readAllLines(logged);
        check("the second run compares f.txt too: " + lines, local.toString().equals(lines.get(2)));
        check("the file on this computer is still the local one", "local content".equals(content(local)));
        return "SMOKE OK compare launched=2 downloaded=g.txt";
    }

    /**
     * An FTP bookmark that transfers the files as ASCII or binary by their type. A text file and a binary file both have
     * line feeds. The server must have the text file with carriage returns and line feeds and the other one as it was.
     * Then the text file is downloaded again, as it is on the server.
     *
     * @param workdir Folder on this computer with text.txt and data.bin
     */
    private static String ftp(final BrowserController browser, final String host, final String port, final String user, final String password, final String workdir) throws Exception {
        final java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        final java.nio.file.Path work = java.nio.file.Paths.get(workdir);
        final BookmarkController bookmarks = browser.getBookmarks();
        final TransferController transfers = TransferController.get();

        onFx(() -> {
            Platform.runLater(bookmarks::add);
            return null;
        });
        await("bookmark dialog", () -> onFx(() -> null != bookmarks.getDialog() && bookmarks.getDialog().isShowing()));
        onFx(() -> {
            final ConnectionDialog dialog = bookmarks.getDialog();
            dialog.getNicknameField().setText("FTP test");
            dialog.getProtocolBox().setValue(ProtocolFactory.get().forName("ftp"));
            dialog.getServerField().setText(host);
            dialog.getPortField().setText(port);
            dialog.getUsernameField().setText(user);
            check("the anonymous option is offered for FTP", dialog.getAnonymousBox().isVisible());
            check("so are the options of FTP", dialog.getMore().isVisible() && dialog.getTransferModeBox().isVisible() && dialog.getConnectModeBox().isVisible());
            dialog.getEncodingBox().setValue("ISO-8859-1");
            dialog.getConnectModeBox().setValue(ch.cyberduck.core.ftp.FTPConnectMode.passive);
            dialog.getTransferModeBox().setValue(dialog.getTransferModeBox().getItems().stream().filter(c -> "auto".equals(c.getValue())).findFirst().orElseThrow());
            dialog.getConnectButton().fire();
            return null;
        });
        await("bookmark saved", () -> onFx(() -> 1 == bookmarks.getList().getItems().size()));
        final Host bookmark = onFx(() -> bookmarks.getList().getItems().get(0));
        check("the transfer mode is kept in the bookmark", "auto".equals(bookmark.getProperty(ch.cyberduck.core.ftp.FTPFileType.MODE)));
        check("so are the character set and the connect mode", "ISO-8859-1".equals(bookmark.getEncoding()) && ch.cyberduck.core.ftp.FTPConnectMode.passive == bookmark.getFTPConnectMode());

        onFx(() -> {
            bookmarks.getList().getSelectionModel().select(bookmark);
            bookmarks.connect();
            return null;
        });
        awaitAnswering("listing after connecting", password, counts, () -> onFx(() -> null != browser.getRendered()));

        // The log shows what was sent to the server and what it answered. The password is not in it.
        menu(browser, "View", "Show Log");
        check("the log is shown", onFx(() -> browser.getLogView().isVisible()));
        await("commands in the log", () -> onFx(() -> browser.getLogView().getItems().stream().anyMatch(l -> l.startsWith("> USER")) &&
            browser.getLogView().getItems().stream().anyMatch(l -> l.startsWith("< 230"))));
        check("the password is not in the log", onFx(() -> browser.getLogView().getItems().stream().noneMatch(l -> l.equals("> PASS " + password))));
        menu(browser, "View", "Show Log");

        final int completed = transfers.getCompleted();
        onFx(() -> {
            browser.upload(List.of(work.resolve("text.txt").toFile(), work.resolve("data.bin").toFile()));
            return null;
        });
        awaitAnswering("upload", password, counts, () -> transfers.getCompleted() >= completed + 1);
        awaitAnswering("uploaded files shown", password, counts, () -> names(browser).containsAll(List.of("text.txt", "data.bin")));

        // Downloads stay binary: the carriage returns that the server has are still there, and the transfer is complete
        final java.nio.file.Path down = java.nio.file.Files.createDirectories(work.resolve("down"));
        PreferencesFactory.get().setProperty("queue.download.folder", down.toString());
        select(browser, "text.txt");
        onFx(() -> {
            browser.getDownloadButton().fire();
            return null;
        });
        awaitAnswering("download", password, counts, () -> transfers.getCompleted() >= completed + 2);
        check("the text file has the line breaks of the server", "line one\r\nline two\r\n".equals(new String(java.nio.file.Files.readAllBytes(down.resolve("text.txt")), java.nio.charset.StandardCharsets.UTF_8)));
        return "SMOKE OK ftp uploaded=2";
    }

    private static boolean onPath(final String program) {
        for(String directory : StringUtils.defaultString(System.getenv("PATH")).split(":")) {
            if(new java.io.File(directory, program).canExecute()) {
                return true;
            }
        }
        return false;
    }

    private static void xdotool(final String... arguments) throws Exception {
        final List<String> command = new java.util.ArrayList<>();
        command.add("xdotool");
        command.addAll(List.of(arguments));
        final Process process = new ProcessBuilder(command).inheritIO().start();
        if(!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException(String.format("Failure running %s", command));
        }
    }

    /**
     * Drag a file and a folder from the listing to another window of the application with the mouse, as a person does it
     * with the file manager. The other window records what it is given. The files have to be there, complete, when the
     * transfer is done.
     *
     * @param directory Folder with f.txt ("dragged content") and the folder d with inner.txt
     */
    private static String dragOut(final BrowserController browser, final String directory) throws Exception {
        if(!onPath("xdotool")) {
            return "SMOKE SKIP dragout, xdotool is not installed";
        }
        mount(browser, directory);
        final java.util.concurrent.atomic.AtomicReference<List<java.io.File>> dropped = new java.util.concurrent.atomic.AtomicReference<>();
        final javafx.stage.Stage target = onFx(() -> {
            final javafx.stage.Stage stage = new javafx.stage.Stage();
            final javafx.scene.layout.StackPane pane = new javafx.scene.layout.StackPane(new javafx.scene.control.Label("Drop here"));
            pane.setOnDragOver(event -> {
                if(event.getDragboard().hasFiles()) {
                    event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
                }
                event.consume();
            });
            pane.setOnDragDropped(event -> {
                dropped.set(new java.util.ArrayList<>(event.getDragboard().getFiles()));
                event.setDropCompleted(true);
                event.consume();
            });
            stage.setScene(new javafx.scene.Scene(pane, 320, 160));
            stage.setX(250);
            stage.setY(720);
            stage.show();
            return stage;
        });
        await("rows", () -> onFx(() -> null != row(browser, "f.txt") && null != row(browser, "d")));
        final StringBuilder result = new StringBuilder("SMOKE OK dragout");
        for(String name : List.of("f.txt", "d")) {
            dropped.set(null);
            final double[] from = onFx(() -> {
                final javafx.geometry.Bounds b = row(browser, name).localToScreen(row(browser, name).getBoundsInLocal());
                return new double[]{b.getMinX() + 60, b.getMinY() + b.getHeight() / 2};
            });
            final double[] to = onFx(() -> {
                final javafx.geometry.Bounds b = target.getScene().getRoot().localToScreen(target.getScene().getRoot().getBoundsInLocal());
                return new double[]{b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2};
            });
            xdotool("mousemove", String.valueOf((int) from[0]), String.valueOf((int) from[1]));
            xdotool("mousedown", "1");
            TimeUnit.MILLISECONDS.sleep(300);
            for(int step = 1; step <= 20; step++) {
                xdotool("mousemove", String.valueOf((int) (from[0] + (to[0] - from[0]) * step / 20)), String.valueOf((int) (from[1] + (to[1] - from[1]) * step / 20)));
                TimeUnit.MILLISECONDS.sleep(50);
            }
            xdotool("mouseup", "1");
            await(String.format("%s dropped", name), () -> null != dropped.get());
            final java.io.File file = dropped.get().get(0);
            check(String.format("the other window got %s as a file called %s", file, name), dropped.get().size() == 1 && file.getName().equals(name));
            // It is downloaded while it is on its way. It is there when it is complete.
            final java.nio.file.Path expected = "d".equals(name) ? file.toPath().resolve("inner.txt") : file.toPath();
            await(String.format("%s downloaded", name), () -> java.nio.file.Files.isRegularFile(expected));
            check("with the content of the server", ("d".equals(name) ? "inner content" : "dragged content").equals(content(expected)));
            result.append(String.format(" %s=ok", name));
        }
        onFx(() -> {
            target.close();
            return null;
        });
        return result.toString();
    }
}
