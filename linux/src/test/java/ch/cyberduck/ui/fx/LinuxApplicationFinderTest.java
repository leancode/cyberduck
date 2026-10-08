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

import ch.cyberduck.core.local.Application;
import ch.cyberduck.core.preferences.PreferencesFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LinuxApplicationFinderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path applications;
    private Path bin;

    @Before
    public void setup() throws Exception {
        PreferencesFactory.set(new LinuxApplicationPreferences(folder.newFolder().toPath().resolve("cyberduck.properties")));
        applications = folder.newFolder("applications").toPath();
        bin = folder.newFolder("bin").toPath();
    }

    private String program(final String name) throws Exception {
        final Path script = bin.resolve(name);
        Files.write(script, "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script.toString();
    }

    private void entry(final String id, final String name, final String command, final String mime) throws Exception {
        Files.write(applications.resolve(id), String.join("\n", "[Desktop Entry]", "Type=Application", "Name=" + name,
            "Exec=" + command + " %F", "MimeType=" + mime, "").getBytes(StandardCharsets.UTF_8));
    }

    private LinuxApplicationFinder finder() {
        return new LinuxApplicationFinder(List.of(applications), "linux.editor.");
    }

    @Test
    public void testFindAllForTextFiles() throws Exception {
        entry("gamma.desktop", "Gamma Edit", program("gamma"), "text/plain;");
        entry("alpha.desktop", "alpha editor", program("alpha"), "text/plain;text/markdown;");
        entry("viewer.desktop", "Picture Viewer", program("viewer"), "image/png;");
        entry("missing.desktop", "Not Installed", "/nonexistent/program", "text/plain;");
        final List<String> names = finder().findAll("notes.txt").stream().map(Application::getName).collect(Collectors.toList());
        // Sorted by name, only text programs and only the ones that are installed
        assertEquals(List.of("alpha editor", "Gamma Edit"), names);
        // Markdown is text for programs that take plain text
        assertEquals(List.of("alpha editor", "Gamma Edit"), finder().findAll("readme.md").stream().map(Application::getName).collect(Collectors.toList()));
        assertEquals(List.of("Picture Viewer"), finder().findAll("a.png").stream().map(Application::getName).collect(Collectors.toList()));
    }

    @Test
    public void testEditorChosenForTheExtensionWins() throws Exception {
        final String command = program("MyEditor");
        PreferencesFactory.get().setProperty("linux.editor.txt", command);
        final Application found = finder().find("Report.TXT");
        // The case of the command is kept, the base class would change it
        assertEquals(command, found.getIdentifier());
        assertTrue(finder().isInstalled(found));
    }

    @Test
    public void testChosenEditorThatIsNotInstalledIsIgnored() {
        PreferencesFactory.get().setProperty("linux.editor.zzz", "/nonexistent/editor");
        assertFalse(finder().isInstalled(finder().find("a.zzz")));
    }

    @Test
    public void testInstalled() throws Exception {
        assertTrue(finder().isInstalled(new LinuxApplication(program("tool") + " --flag", "Tool")));
        assertFalse(finder().isInstalled(new LinuxApplication("no-such-program-xyz", "None")));
        assertFalse(finder().isInstalled(Application.notfound));
        assertFalse(finder().isInstalled(null));
        // A program on the PATH
        assertTrue(finder().isInstalled(new LinuxApplication("sh", "Shell")));
    }

    @Test
    public void testDescriptionOfACommand() throws Exception {
        entry("alpha.desktop", "Alpha Editor", program("alpha"), "text/plain;");
        assertEquals("Alpha Editor", finder().getDescription(program("alpha")).getName());
        assertEquals(new File(program("alpha")).getAbsolutePath(), finder().getDescription(program("alpha")).getIdentifier());
        assertEquals("Meld", finder().getDescription("meld").getName());
        assertEquals(Application.notfound, finder().getDescription(""));
    }
}
