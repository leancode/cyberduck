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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LinuxApplicationPreferencesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testSaveAndLoad() throws IOException {
        final Path file = folder.getRoot().toPath().resolve("nested").resolve("cyberduck.properties");
        final LinuxApplicationPreferences first = new LinuxApplicationPreferences(file);
        first.load();
        first.setProperty("test.key", "väl=ue with spaces");
        first.save();
        assertTrue(Files.exists(file));
        final LinuxApplicationPreferences second = new LinuxApplicationPreferences(file);
        second.load();
        assertEquals("väl=ue with spaces", second.getProperty("test.key"));
    }

    @Test
    public void testDeleteIsPersisted() {
        final Path file = folder.getRoot().toPath().resolve("cyberduck.properties");
        final LinuxApplicationPreferences first = new LinuxApplicationPreferences(file);
        first.load();
        first.setProperty("test.key", "value");
        first.save();
        first.deleteProperty("test.key");
        first.save();
        final LinuxApplicationPreferences second = new LinuxApplicationPreferences(file);
        second.load();
        assertNull(second.getProperty("test.key"));
    }

    @Test
    public void testDefaultsAreNotPersisted() throws IOException {
        final Path file = folder.getRoot().toPath().resolve("cyberduck.properties");
        final LinuxApplicationPreferences preferences = new LinuxApplicationPreferences(file);
        preferences.load();
        preferences.setDefault("test.key", "default");
        assertEquals("default", preferences.getProperty("test.key"));
        preferences.setProperty("test.key", "changed");
        assertEquals("changed", preferences.getProperty("test.key"));
        preferences.deleteProperty("test.key");
        assertEquals("default", preferences.getProperty("test.key"));
        preferences.setProperty("other.key", "value");
        preferences.save();
        assertFalse(new String(Files.readAllBytes(file)).contains("default"));
    }

    @Test
    public void testLoadMissingFile() {
        final LinuxApplicationPreferences preferences = new LinuxApplicationPreferences(folder.getRoot().toPath().resolve("missing.properties"));
        preferences.load();
        assertNull(preferences.getProperty("test.key"));
    }

    @Test
    public void testDefaultFileInSupportDirectory() {
        assertTrue(LinuxApplicationPreferences.defaultFile().endsWith(".duck/cyberduck.properties"));
    }
}
