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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LinuxApplicationPreferencesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testSaveAndLoad() throws IOException {
        final Path file = folder.getRoot().toPath().resolve("nested").resolve("cyberduck.properties");
        final LinuxApplicationPreferences first = new LinuxApplicationPreferences(file);
        first.load();
        first.setProperty("test.key", "väl=ue with spaces");
        first.save();
        assertTrue(Files.exists(file));
        final LinuxApplicationPreferences second = new LinuxApplicationPreferences(file);
        second.load();
        assertEquals("väl=ue with spaces", second.getProperty("test.key"));
    }

    @Test
    public void testDeleteIsPersisted() {
        final Path file = folder.getRoot().toPath().resolve("cyberduck.properties");
        final LinuxApplicationPreferences first = new LinuxApplicationPreferences(file);
        first.load();
        first.setProperty("test.key", "value");
        first.save();
        first.deleteProperty("test.key");
        first.save();
        final LinuxApplicationPreferences second = new LinuxApplicationPreferences(file);
        second.load();
        assertNull(second.getProperty("test.key"));
    }

    @Test
    public void testDefaultsAreNotPersisted() throws IOException {
        final Path file = folder.getRoot().toPath().resolve("cyberduck.properties");
        final LinuxApplicationPreferences preferences = new LinuxApplicationPreferences(file);
        preferences.load();
        preferences.setDefault("test.key", "default");
        assertEquals("default", preferences.getProperty("test.key"));
        preferences.setProperty("test.key", "changed");
        assertEquals("changed", preferences.getProperty("test.key"));
        preferences.deleteProperty("test.key");
        assertEquals("default", preferences.getProperty("test.key"));
        preferences.setProperty("other.key", "value");
        preferences.save();
        assertFalse(new String(Files.readAllBytes(file)).contains("default"));
    }

    @Test
    public void testLoadMissingFile() {
        final LinuxApplicationPreferences preferences = new LinuxApplicationPreferences(folder.getRoot().toPath().resolve("missing.properties"));
        preferences.load();
        assertNull(preferences.getProperty("test.key"));
    }

    @Test
    public void testDefaultFileInSupportDirectory() {
        assertTrue(LinuxApplicationPreferences.defaultFile().endsWith(".duck/cyberduck.properties"));
    }

    @Test
    public void testBugsAreReportedToThisRepository() {
        // The preferences as the program starts them
        ch.cyberduck.core.preferences.PreferencesFactory.set(new LinuxApplicationPreferences(folder.getRoot().toPath().resolve("cyberduck.properties")));
        final ch.cyberduck.core.preferences.Preferences preferences = ch.cyberduck.core.preferences.PreferencesFactory.get();
        assertEquals("https://github.com/leancode/cyberduck-linux/issues/new?body=Version%20{0}", preferences.getProperty("website.bug"));
        // The text of the link has a place for the version
        assertEquals("https://github.com/leancode/cyberduck-linux/issues/new?body=Version%209.6.0",
            java.text.MessageFormat.format(preferences.getProperty("website.bug"), "9.6.0"));
    }

    @Test
    public void testLogsInLikeSshDoes() throws Exception {
        ch.cyberduck.core.preferences.PreferencesFactory.set(new LinuxApplicationPreferences(folder.getRoot().toPath().resolve("cyberduck.properties")));
        final ch.cyberduck.core.preferences.Preferences preferences = ch.cyberduck.core.preferences.PreferencesFactory.get();
        // The agent and the default key files, as ssh has them
        assertTrue(preferences.getBoolean("ssh.authentication.agent.enable"));
        assertTrue(preferences.getBoolean("ssh.authentication.publickey.default.enable"));
        assertEquals("~/.ssh/id_ed25519", preferences.getProperty("ssh.authentication.publickey.default.rsa"));
        assertEquals("~/.ssh/id_rsa", preferences.getProperty("ssh.authentication.publickey.default.dsa"));
        // A server without a key of its own gets the default key that exists
        final java.io.File key = folder.newFile("id_ed25519");
        preferences.setProperty("ssh.authentication.publickey.default.rsa", key.getAbsolutePath());
        final ch.cyberduck.core.Host host = new ch.cyberduck.core.Host(new ch.cyberduck.core.sftp.SFTPProtocol(), "no-such-host.example.invalid");
        host.getCredentials().setUsername("root");
        final ch.cyberduck.core.Credentials configured = new ch.cyberduck.core.sftp.openssh.OpenSSHCredentialsConfigurator().configure(host);
        assertTrue(configured.isPublicKeyAuthentication());
        assertEquals(key.getAbsolutePath(), configured.getIdentity().getAbsolute());
    }

    @Test
    public void testQuickConnectWithoutPasswordUsesTheDefaultKey() throws Exception {
        ch.cyberduck.core.preferences.PreferencesFactory.set(new LinuxApplicationPreferences(folder.getRoot().toPath().resolve("cyberduck.properties")));
        final java.io.File key = folder.newFile("id_ed25519");
        ch.cyberduck.core.preferences.PreferencesFactory.get().setProperty("ssh.authentication.publickey.default.rsa", key.getAbsolutePath());
        final ch.cyberduck.core.ProtocolFactory protocols = new ch.cyberduck.core.ProtocolFactory(java.util.Collections.singleton(new ch.cyberduck.core.sftp.SFTPProtocol() {
            @Override
            public boolean isEnabled() {
                return true;
            }
        }));
        // The user name is given and no password: complete enough for the core not to look for a key
        final ch.cyberduck.core.Host host = HostBuilder.configure(HostBuilder.fromUrl(protocols, "sftp://root@no-such-host.example.invalid:2222"));
        assertEquals("root", host.getCredentials().getUsername());
        assertTrue(host.getCredentials().isPublicKeyAuthentication());
        assertEquals(key.getAbsolutePath(), host.getCredentials().getIdentity().getAbsolute());
        // A password that was typed stays the only way
        final ch.cyberduck.core.Host typed = HostBuilder.configure(HostBuilder.fromUrl(protocols, "sftp://root:secret@no-such-host.example.invalid"));
        assertEquals(false, typed.getCredentials().isPublicKeyAuthentication());
        assertEquals("secret", typed.getCredentials().getPassword());
    }
}
