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

import ch.cyberduck.core.DisabledPasswordStore;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.LocalFactory;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.sftp.SFTPProtocol;
import ch.cyberduck.core.ftp.FTPProtocol;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BookmarkImportTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final SFTPProtocol sftp = new SFTPProtocol() {
        @Override
        public boolean isEnabled() {
            return true;
        }
    };

    @Test
    public void testSshConfig() {
        final List<Host> hosts = BookmarkImport.sshConfig(Arrays.asList(
            "# my servers",
            "Host *",
            "    ServerAliveInterval 30",
            "",
            "Host web",
            "    HostName www.example.net",
            "    User alice",
            "    Port 2222",
            "    IdentityFile ~/.ssh/id_ed25519",
            "Host db1 db2",
            "    User=bob",
            "Host !skip *.internal",
            "    User nobody",
            "Match host x",
            "    User nobody"), sftp, "/home/me");
        assertEquals(3, hosts.size());
        final Host web = hosts.get(0);
        assertEquals("web", web.getNickname());
        assertEquals("www.example.net", web.getHostname());
        assertEquals("alice", web.getCredentials().getUsername());
        assertEquals(2222, web.getPort());
        assertEquals("/home/me/.ssh/id_ed25519", web.getCredentials().getIdentity().getAbsolute());
        // Without a HostName the name is the server
        assertEquals("db1", hosts.get(1).getHostname());
        assertEquals("bob", hosts.get(1).getCredentials().getUsername());
        assertEquals(sftp.getDefaultPort(), hosts.get(1).getPort());
        assertEquals("db2", hosts.get(2).getNickname());
        assertNull(hosts.get(2).getCredentials().getIdentity());
    }

    @Test
    public void testFirstValueWins() {
        final List<Host> hosts = BookmarkImport.sshConfig(Arrays.asList("Host a", "User one", "User two"), sftp, "/home/me");
        assertEquals("one", hosts.get(0).getCredentials().getUsername());
    }

    @Test
    public void testSameBookmark() {
        final Host one = new Host(sftp, "example.net", 22);
        one.getCredentials().setUsername("alice");
        final Host other = new Host(sftp, "EXAMPLE.net", 22);
        other.getCredentials().setUsername("alice");
        assertTrue(BookmarkImport.same(one, other));
        other.getCredentials().setUsername("bob");
        assertFalse(BookmarkImport.same(one, other));
    }

    @Test
    public void testFileZilla() throws Exception {
        final FTPProtocol ftp = new FTPProtocol() {
            @Override
            public boolean isEnabled() {
                return true;
            }
        };
        ProtocolFactory.get().register(ftp, sftp);
        final java.io.File file = folder.newFile("sitemanager.xml");
        Files.write(file.toPath(), ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><FileZilla3><Servers>"
            + "<Server><Host>files.example.net</Host><Port>2200</Port><Protocol>1</Protocol><Type>0</Type><User>alice</User>"
            + "<Pass encoding=\"base64\">c2VjcmV0</Pass><Name>Files</Name></Server>"
            + "</Servers></FileZilla3>").getBytes(StandardCharsets.UTF_8));
        final List<Host> hosts = BookmarkImport.fileZilla(LocalFactory.get(file.getAbsolutePath()), new DisabledPasswordStore());
        assertEquals(1, hosts.size());
        assertEquals("files.example.net", hosts.get(0).getHostname());
        assertEquals(2200, hosts.get(0).getPort());
        assertEquals("alice", hosts.get(0).getCredentials().getUsername());
        assertEquals("sftp", hosts.get(0).getProtocol().getScheme().name());
    }
}
