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

    /**
     * The terminals that are known, the ones of the big desktops first. The alternative x-terminal-emulator comes last,
     * because it stands for a program that may not take the command in the same way.
     */
    private static final List<String> KNOWN = List.of("gnome-terminal", "konsole", "xfce4-terminal", "mate-terminal",
        "tilix", "kitty", "alacritty", "foot", "xterm", "x-terminal-emulator");

    /**
     * Where the desktops keep the terminal of the user
     */
    private static final List<String> SCHEMAS = List.of("org.cinnamon.desktop.default-applications.terminal",
        "org.gnome.desktop.default-applications.terminal", "org.mate.applications-terminal");

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
        // The terminal that the desktop of the user is set to
        for(String schema : SCHEMAS) {
            final String configured = setting(schema);
            if(StringUtils.isNotBlank(configured) && finder.isInstalled(new LinuxApplication(configured, configured))) {
                return configured;
            }
        }
        for(String terminal : KNOWN) {
            if(finder.isInstalled(new LinuxApplication(terminal, terminal))) {
                return terminal;
            }
        }
        return null;
    }

    /**
     * @return The program that a desktop has as its terminal, or null when it has none
     */
    private static String setting(final String schema) {
        try {
            final Process process = new ProcessBuilder("gsettings", "get", schema, "exec")
                .redirectErrorStream(true).redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null"))).start();
            if(!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if(process.exitValue() != 0) {
                return null;
            }
            final String value = StringUtils.strip(new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip(), "'\"");
            return value.isEmpty() ? null : value;
        }
        catch(java.io.IOException e) {
            return null;
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
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
     * Start the terminal. A terminal that is gone within two seconds with a failure did not start, and what it said is
     * the reason.
     *
     * @return Null when the terminal started, otherwise what went wrong
     */
    public static String open(final Host host, final Path folder) {
        final String terminal = find();
        if(null == terminal) {
            return Messages.get("No terminal program was found. Install one or set it in the preferences.");
        }
        final List<String> command = command(terminal, host, folder);
        java.nio.file.Path output = null;
        try {
            output = java.nio.file.Files.createTempFile("cyberduck-terminal", ".log");
            final Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")))
                .start();
            if(process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() != 0) {
                final String said = StringUtils.defaultString(new String(java.nio.file.Files.readAllBytes(output), java.nio.charset.StandardCharsets.UTF_8)).strip();
                return String.format("%s %s", String.format(Messages.get("The terminal {0} did not start.").replace("{0}", "%s"), command.get(0)),
                    StringUtils.abbreviate(said, 400)).strip();
            }
            // Collect the process when it ends so that it does not stay a zombie
            final Thread reaper = new Thread(() -> {
                try {
                    process.waitFor();
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "terminal-reaper");
            reaper.setDaemon(true);
            reaper.start();
            return null;
        }
        catch(java.io.IOException e) {
            return String.format("%s %s", String.format(Messages.get("The terminal {0} did not start.").replace("{0}", "%s"), command.get(0)), e.getMessage());
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        finally {
            if(output != null) {
                output.toFile().deleteOnExit();
            }
        }
    }
}
