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
import ch.cyberduck.core.Path;
import ch.cyberduck.core.local.ApplicationFinder;
import ch.cyberduck.core.local.ApplicationFinderFactory;
import ch.cyberduck.core.preferences.PreferencesFactory;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens a terminal with a shell on the server in the folder that is shown. Only for servers that are reached with SSH.
 */
public final class TerminalLauncher {

    /**
     * Name of the preference with the command of the terminal
     */
    public static final String PROPERTY = "linux.terminal";

    private static final List<String> KNOWN = List.of("x-terminal-emulator", "gnome-terminal", "konsole", "xfce4-terminal",
        "mate-terminal", "tilix", "kitty", "alacritty", "foot", "xterm");

    private TerminalLauncher() {
        //
    }

    /**
     * @return True when the server is reached with SSH
     */
    public static boolean isSupported(final Host host) {
        return null != host && host.getProtocol().getScheme() == ch.cyberduck.core.Scheme.sftp;
    }

    /**
     * @return The command of the terminal that was chosen in the preferences or else the first one that is installed,
     * or null when there is none
     */
    public static String find() {
        return find(ApplicationFinderFactory.get());
    }

    static String find(final ApplicationFinder finder) {
        final String chosen = PreferencesFactory.get().getProperty(PROPERTY);
        if(StringUtils.isNotBlank(chosen)) {
            return chosen;
        }
        for(String terminal : KNOWN) {
            if(finder.isInstalled(new LinuxApplication(terminal, terminal))) {
                return terminal;
            }
        }
        return null;
    }

    /**
     * @param terminal Command of the terminal program
     * @param host     Server, with the key file and the port
     * @param folder   Folder to start in
     * @return Command and arguments that start the shell in the terminal
     */
    public static List<String> command(final String terminal, final Host host, final Path folder) {
        final List<String> ssh = new ArrayList<>(List.of("ssh", "-t"));
        if(host.getPort() != host.getProtocol().getDefaultPort()) {
            ssh.add("-p");
            ssh.add(String.valueOf(host.getPort()));
        }
        final Credentials credentials = host.getCredentials();
        if(null != credentials.getIdentity()) {
            ssh.add("-i");
            ssh.add(credentials.getIdentity().getAbsolute());
        }
        ssh.add(StringUtils.isBlank(credentials.getUsername()) ? host.getHostname()
            : String.format("%s@%s", credentials.getUsername(), host.getHostname()));
        ssh.add(String.format("cd %s && exec \"$SHELL\" -l", quote(folder.getAbsolute())));
        final List<String> command = new ArrayList<>(LinuxApplicationLauncher.tokenize(terminal));
        final String name = command.get(0).substring(command.get(0).lastIndexOf('/') + 1);
        switch(name) {
            case "gnome-terminal":
            case "tilix":
                command.add("--");
                command.addAll(ssh);
                break;
            case "xfce4-terminal":
            case "mate-terminal":
                // These take the whole command as one text
                command.add("-e");
                command.add(ssh.stream().map(TerminalLauncher::quote).collect(java.util.stream.Collectors.joining(" ")));
                break;
            default:
                command.add("-e");
                command.addAll(ssh);
        }
        return command;
    }

    /**
     * Single quotes around the text, for a shell
     */
    static String quote(final String text) {
        return "'" + text.replace("'", "'\\''") + "'";
    }

    /**
     * @return False when no terminal is installed or it could not be started
     */
    public static boolean open(final Host host, final Path folder) {
        final String terminal = find();
        if(null == terminal) {
            return false;
        }
        final List<String> command = command(terminal, host, folder);
        return new LinuxApplicationLauncher().open(new LinuxApplication(command.get(0), command.get(0)), command.subList(1, command.size()));
    }
}
