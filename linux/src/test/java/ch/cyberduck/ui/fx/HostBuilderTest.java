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
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.exception.HostParserException;
import ch.cyberduck.core.nio.LocalProtocol;
import ch.cyberduck.core.sftp.SFTPProtocol;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HostBuilderTest {

    /**
     * Protocols are disabled unless enabled by a profile
     */
    private final SFTPProtocol sftp = new SFTPProtocol() {
        @Override
        public boolean isEnabled() {
            return true;
        }
    };
    private final LocalProtocol local = new LocalProtocol() {
        @Override
        public boolean isEnabled() {
            return true;
        }
    };
    private final ProtocolFactory factory = new ProtocolFactory(new HashSet<>(Arrays.asList(sftp, local)));

    @Test
    public void testUrl() throws Exception {
        final Host host = HostBuilder.fromUrl(factory, "sftp://user@example.net:2222/home");
        assertEquals(sftp, host.getProtocol());
        assertEquals("example.net", host.getHostname());
        assertEquals(2222, host.getPort());
        assertEquals("user", host.getCredentials().getUsername());
        assertEquals("/home", host.getDefaultPath());
    }

    @Test
    public void testFieldsGiveSameBookmarkAsUrl() throws Exception {
        final Host url = HostBuilder.fromUrl(factory, "sftp://user@example.net:2222/home");
        final Host fields = HostBuilder.fromFields(factory, sftp, "example.net", "2222", "user", "", "/home");
        assertEquals(url.getProtocol(), fields.getProtocol());
        assertEquals(url.getHostname(), fields.getHostname());
        assertEquals(url.getPort(), fields.getPort());
        assertEquals(url.getCredentials().getUsername(), fields.getCredentials().getUsername());
        assertEquals(url.getDefaultPath(), fields.getDefaultPath());
    }

    @Test
    public void testServerFieldAcceptsFullUrl() throws Exception {
        final Host host = HostBuilder.fromFields(factory, sftp, " sftp://user@example.net:2222/home ", "", "ignored", "", "");
        assertEquals("example.net", host.getHostname());
        assertEquals(2222, host.getPort());
        assertEquals("user", host.getCredentials().getUsername());
    }

    @Test
    public void testDefaultPortWhenEmpty() throws Exception {
        final Host host = HostBuilder.fromFields(factory, sftp, "example.net", "  ", "", "", "");
        assertEquals(sftp.getDefaultPort(), host.getPort());
        assertEquals("example.net", host.getHostname());
    }

    @Test
    public void testPassword() throws Exception {
        final Host host = HostBuilder.fromFields(factory, sftp, "example.net", "", "user", " pass word ", "");
        assertEquals("user", host.getCredentials().getUsername());
        assertEquals(" pass word ", host.getCredentials().getPassword());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPort() throws Exception {
        HostBuilder.fromFields(factory, sftp, "example.net", "twenty", "user", "", "");
    }

    @Test(expected = HostParserException.class)
    public void testUnknownUrlWithoutHost() throws Exception {
        HostBuilder.fromUrl(factory, "sftp:");
    }

    @Test
    public void testLocalProtocolKeepsDefaults() throws Exception {
        final Host host = HostBuilder.fromFields(factory, local, "ignored", "", "", "", "/tmp");
        assertNotNull(host);
        assertEquals(local.getDefaultHostname(), host.getHostname());
        assertEquals("/tmp", host.getDefaultPath());
    }
}
