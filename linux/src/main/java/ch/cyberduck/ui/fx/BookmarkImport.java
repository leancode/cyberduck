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
import ch.cyberduck.core.LocalFactory;
import ch.cyberduck.core.PasswordStore;
import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.exception.AccessDeniedException;
import ch.cyberduck.core.importer.FilezillaBookmarkCollection;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bookmarks from the programs that other users come from: the server list of FileZilla and the file of ssh
 */
public final class BookmarkImport {

    private BookmarkImport() {
        //
    }

    /**
     * Where FileZilla keeps its servers
     */
    public static Local fileZillaFile() {
        final String home = System.getProperty("user.home");
        return LocalFactory.get(Paths.get(home, ".config", "filezilla", "sitemanager.xml").toString());
    }

    /**
     * Where ssh keeps the names of the servers
     */
    public static Local sshConfigFile() {
        return LocalFactory.get(Paths.get(System.getProperty("user.home"), ".ssh", "config").toString());
    }

    /**
     * @param file     The file sitemanager.xml of FileZilla
     * @param keychain Where to put the passwords that FileZilla stored
     * @return The servers of the file
     */
    public static List<Host> fileZilla(final Local file, final PasswordStore keychain) throws AccessDeniedException {
        final List<Host> found = new ArrayList<>();
        new FilezillaBookmarkCollection(keychain) {
            {
                this.parse(file);
                found.addAll(this);
            }
        };
        return found;
    }

    /**
     * Each host of the file with a name that is not a pattern becomes a bookmark for SFTP. The settings of the entry
     * are the host name, the user, the port and the key file.
     */
    public static List<Host> sshConfig(final Local file, final ProtocolFactory protocols) throws IOException {
        final Protocol sftp = protocols.forName("sftp");
        if(null == sftp) {
            return new ArrayList<>();
        }
        return sshConfig(Files.readAllLines(Paths.get(file.getAbsolute()), StandardCharsets.UTF_8), sftp, System.getProperty("user.home"));
    }

    /**
     * For the scenarios: the hosts of a file at a path
     */
    static List<Host> sshConfigHosts(final java.nio.file.Path file) throws IOException {
        return sshConfig(LocalFactory.get(file.toString()), ProtocolFactory.get());
    }

    static List<Host> sshConfig(final List<String> lines, final Protocol sftp, final String home) {
        // Entries in the order of the file, each with the settings that follow it
        final Map<String, Map<String, String>> entries = new LinkedHashMap<>();
        List<Map<String, String>> current = new ArrayList<>();
        for(String line : lines) {
            final String text = line.strip();
            if(text.isEmpty() || text.startsWith("#")) {
                continue;
            }
            final String[] parts = text.split("[\\s=]+", 2);
            if(parts.length < 2) {
                continue;
            }
            final String key = parts[0].toLowerCase(Locale.ROOT);
            final String value = parts[1].strip();
            switch(key) {
                case "host":
                    current = new ArrayList<>();
                    for(String name : value.split("\\s+")) {
                        if(name.contains("*") || name.contains("?") || name.startsWith("!")) {
                            continue;
                        }
                        final Map<String, String> settings = entries.computeIfAbsent(name, n -> new LinkedHashMap<>());
                        current.add(settings);
                    }
                    break;
                case "match":
                    // Conditions that apply to no single name
                    current = new ArrayList<>();
                    break;
                case "hostname":
                case "user":
                case "port":
                case "identityfile":
                    for(Map<String, String> settings : current) {
                        // The first value of a setting wins, like in ssh
                        settings.putIfAbsent(key, StringUtils.strip(value, "\""));
                    }
                    break;
                default:
                    break;
            }
        }
        final List<Host> hosts = new ArrayList<>();
        for(Map.Entry<String, Map<String, String>> entry : entries.entrySet()) {
            final Map<String, String> settings = entry.getValue();
            final Host host = new Host(sftp, settings.getOrDefault("hostname", entry.getKey()));
            host.setNickname(entry.getKey());
            if(settings.containsKey("port")) {
                try {
                    host.setPort(Integer.parseInt(settings.get("port")));
                }
                catch(NumberFormatException e) {
                    // Keep the default port
                }
            }
            if(settings.containsKey("user")) {
                host.getCredentials().setUsername(settings.get("user"));
            }
            if(settings.containsKey("identityfile")) {
                final String key = settings.get("identityfile");
                host.getCredentials().setIdentity(LocalFactory.get(key.startsWith("~") ? home + key.substring(1) : key));
            }
            hosts.add(host);
        }
        return hosts;
    }

    /**
     * @return True when the bookmarks have the same server, port, account and protocol
     */
    public static boolean same(final Host one, final Host other) {
        return one.getProtocol().equals(other.getProtocol())
            && StringUtils.equalsIgnoreCase(one.getHostname(), other.getHostname())
            && one.getPort() == other.getPort()
            && StringUtils.equals(StringUtils.defaultString(one.getCredentials().getUsername()), StringUtils.defaultString(other.getCredentials().getUsername()));
    }
}
