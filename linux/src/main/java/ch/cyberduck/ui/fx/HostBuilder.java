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
import ch.cyberduck.core.HostParser;
import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.exception.HostParserException;

import org.apache.commons.lang3.StringUtils;

/**
 * Create a bookmark from the values typed in the connection dialog. Does not depend on the user interface.
 */
public final class HostBuilder {

    private HostBuilder() {
        //
    }

    /**
     * @param factory Known protocols
     * @param url     URL including scheme such as {@code sftp://user@example.net:2222/home}
     */
    public static Host fromUrl(final ProtocolFactory factory, final String url) throws HostParserException {
        return new HostParser(factory).get(StringUtils.trim(url));
    }

    /**
     * Create a bookmark from the fields of the dialog. Values not given or not configurable for the protocol keep the
     * defaults of the protocol.
     *
     * @param protocol Selected protocol
     * @param server   Hostname. A full URL including the scheme is parsed instead of using the other fields.
     * @param port     Port number or empty for the default port of the protocol
     * @param username Login name or empty
     * @param password Password or empty
     * @param path     Initial path or empty
     * @param factory  Known protocols for parsing a URL
     * @throws IllegalArgumentException If the port is not a number
     */
    public static Host fromFields(final ProtocolFactory factory, final Protocol protocol, final String server, final String port,
                                  final String username, final String password, final String path) throws HostParserException {
        if(StringUtils.contains(server, "://")) {
            return fromUrl(factory, server);
        }
        final Host host = new Host(protocol);
        if(protocol.isHostnameConfigurable() && StringUtils.isNotBlank(server)) {
            host.setHostname(StringUtils.trim(server));
        }
        if(protocol.isPortConfigurable() && StringUtils.isNotBlank(port)) {
            try {
                host.setPort(Integer.parseInt(StringUtils.trim(port)));
            }
            catch(NumberFormatException e) {
                throw new IllegalArgumentException(String.format("Invalid port number %s", port), e);
            }
        }
        final Credentials credentials = new Credentials();
        if(StringUtils.isNotBlank(username)) {
            credentials.setUsername(StringUtils.trim(username));
        }
        if(StringUtils.isNotEmpty(password)) {
            credentials.setPassword(password);
        }
        host.setCredentials(credentials);
        if(StringUtils.isNotBlank(path)) {
            host.setDefaultPath(StringUtils.trim(path));
        }
        return host;
    }

    /**
     * Copy the values a user can edit in the dialog, keeping the identity of the target bookmark
     *
     * @param from Edited values
     * @param to   Bookmark to change
     */
    public static void copy(final Host from, final Host to) {
        to.setProtocol(from.getProtocol());
        to.setHostname(from.getHostname());
        to.setPort(from.getPort());
        to.setDefaultPath(from.getDefaultPath());
        to.setNickname(from.getNickname());
        to.getCredentials().setUsername(from.getCredentials().getUsername());
    }
}
