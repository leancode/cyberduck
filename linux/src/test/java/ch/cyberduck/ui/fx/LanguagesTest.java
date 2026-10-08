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

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LanguagesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testAvailable() throws Exception {
        folder.newFolder("de.lproj");
        folder.newFolder("pt_BR.lproj");
        folder.newFolder("fr.lproj");
        folder.newFolder("Base.lproj");
        folder.newFile("xx.lproj");
        folder.newFolder("images");
        // Ordered by the name in the own language: Deutsch, français, Português (Brasil)
        assertEquals(List.of("de", "fr", "pt_BR"), Languages.available(folder.getRoot()));
    }

    @Test
    public void testNoFolder() {
        assertEquals(List.of(), Languages.available(new File(folder.getRoot(), "missing")));
    }

    @Test
    public void testNameInTheOwnLanguage() {
        assertEquals("Deutsch", Languages.name("de"));
        assertEquals("Français", Languages.name("fr"));
        assertEquals("Português (Brasil)", Languages.name("pt_BR"));
        assertEquals("日本語", Languages.name("ja"));
    }
}
