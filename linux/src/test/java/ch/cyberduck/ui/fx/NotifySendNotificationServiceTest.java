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

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NotifySendNotificationServiceTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path fake(final Path log) throws Exception {
        final Path script = folder.newFile("notify-send").toPath();
        // One line per argument, a line with the separator after each run
        Files.write(script, ("#!/bin/sh\nfor a in \"$@\"; do printf '%s\\n' \"$a\" >> '" + log + "'; done\necho '---' >> '" + log + "'\n").getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    @Test
    public void testNotify() throws Exception {
        final Path log = folder.newFile("log").toPath();
        final NotifySendNotificationService service = new NotifySendNotificationService(this.fake(log).toString());
        service.notify("photos", "2f4a", "Download complete", "photos");
        final List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        assertEquals("--app-name=Cyberduck", lines.get(0));
        assertEquals("--hint=string:x-canonical-private-synchronous:2f4a", lines.get(1));
        assertEquals("--", lines.get(2));
        assertEquals("Download complete", lines.get(3));
        assertEquals("photos", lines.get(4));
        assertEquals("---", lines.get(5));
    }

    @Test
    public void testTitleStartingWithDashIsNotAnOption() throws Exception {
        final Path log = folder.newFile("log").toPath();
        new NotifySendNotificationService(this.fake(log).toString()).notify(null, null, "-h", "-d");
        final List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        assertEquals("--", lines.get(1));
        assertEquals("-h", lines.get(2));
        assertEquals("-d", lines.get(3));
    }

    @Test
    public void testMissingToolIsIgnored() {
        final NotifySendNotificationService service = new NotifySendNotificationService("/nonexistent/notify-send");
        service.notify("a", "b", "Download complete", "a");
        service.notify("a", "b", "Download complete", "a");
    }

    /**
     * Needs libnotify-bin. Without a notification daemon the tool fails, which is only logged.
     */
    @Test
    public void testRealTool() {
        Assume.assumeTrue("notify-send is not installed", new java.io.File("/usr/bin/notify-send").canExecute());
        new NotifySendNotificationService().notify("a", "b", "Download complete", "a");
        assertTrue(true);
    }
}
