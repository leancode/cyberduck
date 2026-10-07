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
import ch.cyberduck.core.exception.LoginCanceledException;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.sftp.SFTPProtocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FxLoginCallbackTest {

    private final Host host = new Host(new SFTPProtocol(), "example.net");

    @Test
    public void testPromptReturnsInput() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        dialogs.credentials = new Credentials("user", "secret").setSaved(true);
        final Credentials input = new FxLoginCallback(new FxController(), dialogs).prompt(host, "user", "Login", "Reason", new LoginOptions());
        assertSame(dialogs.credentials, input);
        assertEquals("Login", dialogs.titles.get(0));
    }

    @Test(expected = LoginCanceledException.class)
    public void testPromptCancelled() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        new FxLoginCallback(new FxController(), dialogs).prompt(host, "user", "Login", "Reason", new LoginOptions());
    }

    @Test
    public void testPasswordPrompt() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        dialogs.credentials = new Credentials(null, "secret");
        assertEquals("secret", new FxPasswordCallback(dialogs).prompt(host, "Password", "Reason", new LoginOptions().user(false)).getPassword());
    }

    @Test(expected = LoginCanceledException.class)
    public void testPasswordPromptCancelled() throws Exception {
        new FxPasswordCallback(new FakeDialogService()).prompt(host, "Password", "Reason", new LoginOptions());
    }

    @Test
    public void testWarnAccepted() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        new FxLoginCallback(new FxController(), dialogs).warn(host, "Unsecured", "Plaintext", "Continue", "Disconnect", null);
        assertEquals("Plaintext", dialogs.messages.get(0));
    }

    @Test(expected = LoginCanceledException.class)
    public void testWarnDeclined() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        dialogs.confirmation = DialogService.Confirmation.NO;
        new FxLoginCallback(new FxController(), dialogs).warn(host, "Unsecured", "Plaintext", "Continue", "Disconnect", null);
    }

    @Test
    public void testWarnSuppressedIsRemembered() throws Exception {
        final String preference = "test.connection.unsecure.example.net";
        PreferencesFactory.get().deleteProperty(preference);
        final FakeDialogService dialogs = new FakeDialogService();
        dialogs.confirmation = new DialogService.Confirmation(true, true);
        final FxLoginCallback callback = new FxLoginCallback(new FxController(), dialogs);
        callback.warn(host, "Unsecured", "Plaintext", "Continue", "Disconnect", preference);
        assertTrue(PreferencesFactory.get().getBoolean(preference));
        // Not asked again
        dialogs.confirmation = DialogService.Confirmation.NO;
        callback.warn(host, "Unsecured", "Plaintext", "Continue", "Disconnect", preference);
        assertEquals(1, dialogs.messages.size());
        PreferencesFactory.get().deleteProperty(preference);
        assertFalse(PreferencesFactory.get().getBoolean(preference));
    }
}
