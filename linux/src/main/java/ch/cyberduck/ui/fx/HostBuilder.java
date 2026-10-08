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
import ch.cyberduck.core.LocalFactory;
import ch.cyberduck.core.HostParser;
import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.exception.HostParserException;
import ch.cyberduck.core.ftp.FTPConnectMode;
import ch.cyberduck.core.ftp.FTPFileType;
import ch.cyberduck.core.preferences.PreferencesFactory;

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
     * Options of a protocol that are not in the main fields of the dialog
     *
     * @param host         Bookmark made from the fields
     * @param anonymous    Log in without an account. For protocols that allow it.
     * @param encoding     Character set of the file names or blank for the default
     * @param connectMode  FTP connect mode, or default
     * @param transferMode FTP transfer mode: binary, ascii or auto, or blank for the setting of the preferences
     */
    public static void options(final Host host, final boolean anonymous, final String encoding, final FTPConnectMode connectMode, final String transferMode) {
        final Protocol protocol = host.getProtocol();
        if(anonymous && protocol.isAnonymousConfigurable()) {
            host.getCredentials().setUsername(PreferencesFactory.get().getProperty("connection.login.anon.name"));
            host.getCredentials().setPassword(StringUtils.EMPTY);
        }
        if(protocol.isEncodingConfigurable() && StringUtils.isNotBlank(encoding)) {
            host.setEncoding(encoding);
        }
        if(protocol.getType() == Protocol.Type.ftp) {
            if(connectMode != null) {
                host.setFTPConnectMode(connectMode);
            }
            // Blank is the setting of the preferences
            setOrClear(host, FTPFileType.MODE, transferMode);
        }
    }

    /**
     * Log in with a private key file instead of a password. A blank path removes the key.
     */
    public static void identity(final Host host, final String file) {
        if(host.getProtocol().isPrivateKeyConfigurable() && StringUtils.isNotBlank(file)) {
            host.getCredentials().setIdentity(LocalFactory.get(StringUtils.trim(file)));
        }
        else {
            host.getCredentials().setIdentity(null);
        }
    }

    /**
     * Set a custom property of the bookmark or remove it for a blank value, so that the default applies again
     */
    static void setOrClear(final Host host, final String key, final String value) {
        if(StringUtils.isNotBlank(value)) {
            host.setProperty(key, value);
        }
        else if(host.getCustom().containsKey(key)) {
            final java.util.Map<String, String> custom = new java.util.HashMap<>(host.getCustom());
            custom.remove(key);
            host.setCustom(custom);
        }
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
        to.getCredentials().setIdentity(from.getCredentials().getIdentity());
        to.setEncoding(from.getEncoding());
        to.setFTPConnectMode(from.getFTPConnectMode());
        setOrClear(to, FTPFileType.MODE, from.getProperty(FTPFileType.MODE));
    }
}
