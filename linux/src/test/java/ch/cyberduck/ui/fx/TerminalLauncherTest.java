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
import ch.cyberduck.core.sftp.SFTPProtocol;

import org.junit.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TerminalLauncherTest {

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
