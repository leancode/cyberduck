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
import ch.cyberduck.core.LocalFactory;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.sftp.SFTPProtocol;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TerminalLauncherTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Before
    public void setup() throws Exception {
        PreferencesFactory.set(new LinuxApplicationPreferences(temporary.newFolder().toPath().resolve("cyberduck.properties")));
    }

    private java.io.File script(final String body) throws Exception {
        final java.io.File file = temporary.newFile();
        java.nio.file.Files.write(file.toPath(), ("#!/bin/sh\n" + body + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        file.setExecutable(true);
        return file;
    }

    @Test
    public void testStartedTerminalGetsTheCommand() throws Exception {
        final java.io.File log = temporary.newFile();
        final java.io.File terminal = script("echo \"$@\" > " + log.getAbsolutePath());
        PreferencesFactory.get().setProperty(TerminalLauncher.PROPERTY, terminal.getAbsolutePath());
        assertNull(TerminalLauncher.open(host(), folder));
        final long deadline = System.currentTimeMillis() + 5000;
        while(log.length() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        final String given = new String(java.nio.file.Files.readAllBytes(log.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(given, given.startsWith("-e ssh -t -p 2222 alice@example.net"));
    }

    @Test
    public void testTerminalThatFailsSaysWhy() throws Exception {
        final java.io.File terminal = script("echo 'Unknown option -t' >&2; exit 2");
        PreferencesFactory.get().setProperty(TerminalLauncher.PROPERTY, terminal.getAbsolutePath());
        final String failure = TerminalLauncher.open(host(), folder);
        assertTrue(failure, failure.contains("did not start") && failure.contains("Unknown option -t"));
    }

    @Test
    public void testTerminalThatIsMissingSaysWhy() {
        PreferencesFactory.get().setProperty(TerminalLauncher.PROPERTY, "/nonexistent/terminal");
        final String failure = TerminalLauncher.open(host(), folder);
        assertTrue(failure, failure.contains("did not start"));
    }

    private final Path folder = new Path("/home/o'brien/data", EnumSet.of(Path.Type.directory));

    private Host host() {
        final Host host = new Host(new SFTPProtocol(), "example.net", 2222);
        host.getCredentials().setUsername("alice");
        return host;
    }

    @Test
    public void testOnlyForSsh() {
        assertTrue(TerminalLauncher.isSupported(host()));
        assertFalse(TerminalLauncher.isSupported(new Host(new ch.cyberduck.core.ftp.FTPProtocol(), "example.net")));
        assertFalse(TerminalLauncher.isSupported(null));
    }

    @Test
    public void testGnomeTerminalTakesTheCommandAfterTwoDashes() {
        final List<String> command = TerminalLauncher.command("gnome-terminal", host(), folder);
        assertEquals(List.of("gnome-terminal", "--", "ssh", "-t", "-p", "2222", "alice@example.net",
            "cd '/home/o'\\''brien/data' && exec \"$SHELL\" -l"), command);
    }

    @Test
    public void testOtherTerminalsTakeTheCommandAfterDashE() {
        final List<String> command = TerminalLauncher.command("xterm", host(), folder);
        assertEquals(List.of("xterm", "-e", "ssh"), command.subList(0, 3));
    }

    @Test
    public void testSomeTerminalsTakeOneText() {
        final List<String> command = TerminalLauncher.command("xfce4-terminal", host(), folder);
        assertEquals(3, command.size());
        assertEquals("-e", command.get(1));
        assertTrue(command.get(2).startsWith("'ssh' '-t' '-p' '2222'"));
    }

    @Test
    public void testKeyFileAndDefaultPort() {
        final Host host = new Host(new SFTPProtocol(), "example.net");
        host.getCredentials().setIdentity(LocalFactory.get("/home/alice/.ssh/id_ed25519"));
        final List<String> command = TerminalLauncher.command("kitty", host, folder);
        assertTrue(command.containsAll(List.of("-i", "/home/alice/.ssh/id_ed25519")));
        assertFalse(command.contains("-p"));
        // No account name: ssh uses its own
        assertTrue(command.contains("example.net"));
    }

    @Test
    public void testProgramWithArguments() {
        final List<String> command = TerminalLauncher.command("alacritty --class cyberduck", host(), folder);
        assertEquals(List.of("alacritty", "--class", "cyberduck", "-e", "ssh"), command.subList(0, 5));
    }
}
