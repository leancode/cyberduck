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

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DesktopEntryTest {

    @Test
    public void testParse() {
        final DesktopEntry entry = DesktopEntry.parse("org.example.editor.desktop", Arrays.asList(
            "[Desktop Entry]",
            "Type=Application",
            "Name=Example Editor",
            "Name[de]=Beispiel Editor",
            "Exec=example-editor --new-window %F",
            "MimeType=text/plain;text/markdown;",
            "Categories=Utility;TextEditor;",
            "[Desktop Action new]",
            "Name=New",
            "Exec=example-editor --other"));
        assertNotNull(entry);
        assertEquals("Example Editor", entry.getName());
        assertEquals("example-editor --new-window", entry.getCommand());
        assertEquals(Arrays.asList("text/plain", "text/markdown"), entry.getMimeTypes());
        assertTrue(entry.getCategories().contains("TextEditor"));
        assertFalse(entry.isHidden());
    }

    @Test
    public void testPercentSign() {
        assertEquals("tool 100% --file", DesktopEntry.parse("a.desktop", Arrays.asList("[Desktop Entry]", "Type=Application", "Name=A", "Exec=tool 100%% --file %u")).getCommand());
    }

    @Test
    public void testHiddenAndNotApplications() {
        assertTrue(DesktopEntry.parse("a.desktop", Arrays.asList("[Desktop Entry]", "Type=Application", "Name=A", "Exec=a", "NoDisplay=true")).isHidden());
        assertNull(DesktopEntry.parse("b.desktop", Arrays.asList("[Desktop Entry]", "Type=Link", "Name=B", "URL=https://example.net")));
        assertNull(DesktopEntry.parse("c.desktop", Arrays.asList("[Desktop Entry]", "Type=Application", "Name=No command")));
    }

    @Test
    public void testPackagedEntryHandlesTheLoginCallback() {
        final DesktopEntry entry = DesktopEntry.parse(java.nio.file.Paths.get("..", "setup", "linux", "Cyberduck.desktop"));
        // The web browser hands the answer of a login to the application through these
        assertTrue(entry.getMimeTypes().contains("x-scheme-handler/io.cyberduck"));
        assertTrue(entry.getMimeTypes().contains("x-scheme-handler/x-cyberduck-action"));
        assertTrue(entry.getMimeTypes().contains("x-scheme-handler/sftp"));
    }
}
