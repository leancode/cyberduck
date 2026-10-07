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

import ch.cyberduck.core.Credentials;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.LoginOptions;
import ch.cyberduck.core.sftp.SFTPProtocol;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Opens the real dialogs, fills them in and presses the buttons. Needs a display.
 */
public class FxDialogServiceTest {

    private final FxController controller = new FxController();
    private final FxDialogService service = new FxDialogService(controller);
    private final Host host = new Host(new SFTPProtocol(), "example.net");

    @Before
    public void toolkit() {
        FxToolkit.init();
    }

    private static <T> T onFx(final Callable<T> callable) throws Exception {
        final FutureTask<T> task = new FutureTask<>(callable);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    private static DialogPane awaitDialog() throws Exception {
        final long deadline = System.currentTimeMillis() + 20000;
        while(System.currentTimeMillis() < deadline) {
            final DialogPane pane = onFx(() -> {
                for(Window window : Window.getWindows()) {
                    if(window.isShowing() && window.getScene() != null && window.getScene().getRoot() instanceof DialogPane p) {
                        return p;
                    }
                }
                return null;
            });
            if(pane != null) {
                return pane;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("No dialog shown");
    }

    private static void press(final DialogPane pane, final ButtonType type) throws Exception {
        onFx(() -> {
            ((javafx.scene.control.Button) pane.lookupButton(type)).fire();
            return null;
        });
    }

    /**
     * Run the dialog on a background thread like a background action would and return its result
     */
    private <T> AtomicReference<T> ask(final Callable<T> question, final Thread[] holder) {
        final AtomicReference<T> result = new AtomicReference<>();
        holder[0] = new Thread(() -> {
            try {
                result.set(question.call());
            }
            catch(Exception e) {
                throw new IllegalStateException(e);
            }
        });
        holder[0].start();
        return result;
    }

    @Test(timeout = 60000L)
    public void testCredentials() throws Exception {
        final Thread[] thread = new Thread[1];
        final AtomicReference<Credentials> result = ask(() -> service.credentials(host, "user", "Login failed", "Wrong password.",
            new LoginOptions().user(true).password(true).keychain(true).save(false)), thread);
        final DialogPane pane = awaitDialog();
        onFx(() -> {
            assertEquals("user", pane.lookup(".text-field") instanceof TextField t ? t.getText() : null);
            ((PasswordField) pane.lookup(".password-field")).setText("secret");
            ((CheckBox) pane.lookup(".check-box")).setSelected(true);
            return null;
        });
        press(pane, pane.getButtonTypes().get(0));
        thread[0].join();
        assertNotNull(result.get());
        assertEquals("user", result.get().getUsername());
        assertEquals("secret", result.get().getPassword());
        assertTrue(result.get().isSaved());
    }

    @Test(timeout = 60000L)
    public void testCredentialsCancelled() throws Exception {
        final Thread[] thread = new Thread[1];
        final AtomicReference<Credentials> result = ask(() -> service.credentials(host, "user", "Login", "", new LoginOptions()), thread);
        final DialogPane pane = awaitDialog();
        press(pane, ButtonType.CANCEL);
        thread[0].join();
        assertNull(result.get());
    }

    @Test(timeout = 60000L)
    public void testConfirmAccepted() throws Exception {
        final Thread[] thread = new Thread[1];
        final AtomicReference<DialogService.Confirmation> result = ask(() -> service.confirm("Unsecured connection", "Password will be sent in plaintext.", "Continue", "Disconnect", true), thread);
        final DialogPane pane = awaitDialog();
        onFx(() -> {
            ((CheckBox) pane.lookup(".check-box")).setSelected(true);
            return null;
        });
        press(pane, pane.getButtonTypes().get(0));
        thread[0].join();
        assertTrue(result.get().accepted());
        assertTrue(result.get().suppressed());
    }

    @Test(timeout = 60000L)
    public void testConfirmDeclined() throws Exception {
        final Thread[] thread = new Thread[1];
        final AtomicReference<DialogService.Confirmation> result = ask(() -> service.confirm("Title", "Message", "Continue", "Cancel", false), thread);
        final DialogPane pane = awaitDialog();
        press(pane, pane.getButtonTypes().get(1));
        thread[0].join();
        assertFalse(result.get().accepted());
        assertFalse(result.get().suppressed());
    }

    @Test(timeout = 60000L)
    public void testErrorWaitsUntilAcknowledged() throws Exception {
        final Thread[] thread = new Thread[1];
        final AtomicReference<String> done = ask(() -> {
            service.error("Connection failed", "Connection refused.");
            return "done";
        }, thread);
        final DialogPane pane = awaitDialog();
        assertEquals("Connection failed", pane.getHeaderText());
        assertEquals("Connection refused.", pane.getContentText());
        assertNull("Blocked while the dialog is showing", done.get());
        press(pane, ButtonType.OK);
        thread[0].join();
        assertEquals("done", done.get());
    }
}
