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
import ch.cyberduck.core.Local;
import ch.cyberduck.core.Scheme;
import ch.cyberduck.core.UnsecureHostPasswordStore;
import ch.cyberduck.core.exception.AccessDeniedException;
import ch.cyberduck.core.sftp.SFTPProtocol;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SecretToolPasswordStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final SFTPProtocol sftp = new SFTPProtocol() {
        @Override
        public boolean isEnabled() {
            return true;
        }
    };

    private UnsecureHostPasswordStore fallback() throws Exception {
        return new UnsecureHostPasswordStore(new Local(new File(folder.newFolder(), "credentials").getAbsolutePath()));
    }

    /**
     * A stand-in for secret-tool that keeps the secrets in files and logs its arguments
     */
    private String fake(final Path store, final Path log) throws Exception {
        final Path script = folder.newFile("secret-tool").toPath();
        Files.write(script, String.join("\n",
            "#!/bin/sh",
            "echo \"$@\" >> '" + log + "'",
            "command=$1; shift",
            "[ \"$command\" = store ] && shift",
            "key=$(echo \"$@\" | tr -c 'A-Za-z0-9\\n' '_')",
            "case $command in",
            "  store) cat > '" + store + "'/\"$key\" ;;",
            "  lookup) [ -f '" + store + "'/\"$key\" ] || exit 1; cat '" + store + "'/\"$key\" ;;",
            "  clear) rm -f '" + store + "'/\"$key\" ;;",
            "esac",
            "").getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script.toString();
    }

    @Test
    public void testStoreFindAndDelete() throws Exception {
        final Path store = folder.newFolder().toPath();
        final Path log = folder.newFile("log").toPath();
        final SecretToolPasswordStore passwords = new SecretToolPasswordStore(this.fake(store, log), this.fallback());
        assertTrue(passwords.isInstalled());
        assertNull(passwords.getPassword(Scheme.sftp, 22, "example.net", "alice"));
        passwords.addPassword(Scheme.sftp, 22, "example.net", "alice", "s3cret pässword");
        assertEquals("s3cret pässword", passwords.getPassword(Scheme.sftp, 22, "example.net", "alice"));
        // Other account and other port are not found
        assertNull(passwords.getPassword(Scheme.sftp, 22, "example.net", "bob"));
        assertNull(passwords.getPassword(Scheme.sftp, 2222, "example.net", "alice"));
        passwords.deletePassword(Scheme.sftp, 22, "example.net", "alice");
        assertNull(passwords.getPassword(Scheme.sftp, 22, "example.net", "alice"));
        assertFalse("The password must not be on the command line", new String(Files.readAllBytes(log), StandardCharsets.UTF_8).contains("s3cret"));
    }

    @Test
    public void testGenericPassword() throws Exception {
        final Path store = folder.newFolder().toPath();
        final SecretToolPasswordStore passwords = new SecretToolPasswordStore(this.fake(store, folder.newFile("log").toPath()), this.fallback());
        passwords.addPassword("Some Service", "alice", "secret");
        assertEquals("secret", passwords.getPassword("Some Service", "alice"));
        assertNull(passwords.getPassword("Other Service", "alice"));
        passwords.deletePassword("Some Service", "alice");
        assertNull(passwords.getPassword("Some Service", "alice"));
    }

    @Test
    public void testBookmarkLogin() throws Exception {
        final Path store = folder.newFolder().toPath();
        final SecretToolPasswordStore passwords = new SecretToolPasswordStore(this.fake(store, folder.newFile("log").toPath()), this.fallback());
        final Host bookmark = new Host(sftp, "example.net", new Credentials("alice", "secret"));
        bookmark.getCredentials().setSaved(true);
        passwords.save(bookmark);
        final Host other = new Host(sftp, "example.net", new Credentials("alice"));
        // Deleting a bookmark of a protocol with private keys looks up the passphrase of the identity
        other.getCredentials().setIdentity(new Local("/nonexistent/id_rsa"));
        assertEquals("secret", passwords.findLoginPassword(other));
        passwords.delete(other);
        assertNull(passwords.findLoginPassword(other));
    }

    @Test
    public void testFallbackWithoutTool() throws Exception {
        final SecretToolPasswordStore passwords = new SecretToolPasswordStore("/nonexistent/secret-tool", this.fallback());
        assertFalse(passwords.isInstalled());
        assertNull(passwords.getPassword(Scheme.sftp, 22, "example.net", "alice"));
        passwords.addPassword(Scheme.sftp, 22, "example.net", "alice", "secret");
        assertEquals("secret", passwords.getPassword(Scheme.sftp, 22, "example.net", "alice"));
        passwords.deletePassword(Scheme.sftp, 22, "example.net", "alice");
        assertNull(passwords.getPassword(Scheme.sftp, 22, "example.net", "alice"));
    }

    /**
     * Needs libsecret-tools and a Secret Service on the session bus, for example <code>dbus-run-session</code> with an
     * unlocked gnome-keyring.
     */
    @Test
    public void testRealKeyring() throws Exception {
        final SecretToolPasswordStore passwords = new SecretToolPasswordStore("secret-tool", fallback(), 10);
        Assume.assumeTrue("secret-tool is not installed", passwords.isInstalled());
        final String host = String.format("%s.example.net", UUID.randomUUID());
        try {
            try {
                assertNull(passwords.getPassword(Scheme.sftp, 22, host, "alice"));
                passwords.addPassword(Scheme.sftp, 22, host, "alice", "s3cret pässword");
            }
            catch(AccessDeniedException e) {
                Assume.assumeNoException("No unlocked Secret Service available", e);
            }
            assertEquals("s3cret pässword", passwords.getPassword(Scheme.sftp, 22, host, "alice"));
            passwords.addPassword(Scheme.sftp, 22, host, "alice", "changed");
            assertEquals("changed", passwords.getPassword(Scheme.sftp, 22, host, "alice"));
        }
        finally {
            try {
                passwords.deletePassword(Scheme.sftp, 22, host, "alice");
            }
            catch(AccessDeniedException e) {
                // Not stored
            }
        }
    }

    /**
     * A tool that waits for the keyring to be unlocked must not block the application
     */
    @Test(timeout = 30000)
    public void testTimeout() throws Exception {
        final Path script = folder.newFile("slow-secret-tool").toPath();
        Files.write(script, "#!/bin/sh\nexec sleep 60\n".getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        final SecretToolPasswordStore passwords = new SecretToolPasswordStore(script.toString(), this.fallback(), 2);
        try {
            passwords.addPassword(Scheme.sftp, 22, "example.net", "alice", "secret");
            fail("Expected a timeout");
        }
        catch(AccessDeniedException e) {
            assertTrue(e.getDetail(), e.getDetail().contains("Timeout"));
        }
    }
}
