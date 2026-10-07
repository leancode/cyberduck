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

import ch.cyberduck.core.Host;
import ch.cyberduck.core.Local;
import ch.cyberduck.core.sftp.SFTPProtocol;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.security.KeyPairGenerator;
import java.security.PublicKey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FxHostKeyCallbackTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static PublicKey key() throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair().getPublic();
    }

    private final Host host = new Host(new SFTPProtocol(), "example.net", 22);

    @Test
    public void testUnknownKeyAcceptedIsRemembered() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        final FxHostKeyCallback callback = new FxHostKeyCallback(new Local(folder.newFile("known_hosts").getAbsolutePath()), dialogs);
        final PublicKey key = key();
        assertTrue(callback.verify(host, key));
        assertEquals(1, dialogs.titles.size());
        assertTrue(dialogs.titles.get(0).contains("example.net"));
        assertTrue(dialogs.messages.get(0).contains("fingerprint"));
        // Known now
        assertTrue(callback.verify(host, key));
        assertEquals(1, dialogs.titles.size());
    }

    /**
     * The verifier of the core turns a cancelled prompt into a failed verification, which aborts the connection
     */
    @Test
    public void testUnknownKeyDenied() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        dialogs.confirmation = DialogService.Confirmation.NO;
        final FxHostKeyCallback callback = new FxHostKeyCallback(new Local(folder.newFile("known_hosts").getAbsolutePath()), dialogs);
        final PublicKey key = key();
        assertFalse(callback.verify(host, key));
        assertEquals(1, dialogs.titles.size());
        // Not remembered, asked again
        assertFalse(callback.verify(host, key));
        assertEquals(2, dialogs.titles.size());
    }

    @Test
    public void testChangedKeyDenied() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        final FxHostKeyCallback callback = new FxHostKeyCallback(new Local(folder.newFile("known_hosts").getAbsolutePath()), dialogs);
        assertTrue(callback.verify(host, key()));
        dialogs.confirmation = DialogService.Confirmation.NO;
        // A different key for the same host
        assertFalse(callback.verify(host, key()));
        assertTrue(dialogs.titles.get(1).startsWith("Changed fingerprint"));
    }
}
