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

import ch.cyberduck.core.Local;
import ch.cyberduck.core.LocaleFactory;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.i18n.RegexLocale;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MessagesTest {

    private RegexLocale locale;

    @Before
    public void load() {
        // The translations are unpacked next to the classes by the build
        final File resources = new File("target");
        Assume.assumeTrue("No translations unpacked", new File(resources, "de.lproj").isDirectory());
        locale = new RegexLocale(new Local(resources.getAbsolutePath()));
        LocaleFactory.set(locale);
    }

    @After
    public void reset() {
        LocaleFactory.set(null);
    }

    @Test
    public void testGerman() {
        locale.setDefault("de");
        assertEquals("Aktualisieren", Messages.get("Refresh"));
        assertEquals("Löschen", Messages.get("Delete"));
        assertEquals("Dateitransfers", Messages.get("Transfers"));
        assertEquals("Neuer Ordner", Messages.get("New Folder"));
        assertEquals("Benutzername", Messages.get("Username"));
        assertEquals("Dateiname", Messages.get("Filename"));
        assertEquals("Herunterladen", Messages.get("Download"));
    }

    @Test
    public void testFrench() {
        locale.setDefault("fr");
        assertEquals("Actualiser", Messages.get("Refresh"));
    }

    @Test
    public void testEnglishWhenNotTranslated() {
        locale.setDefault("de");
        assertEquals("Something nobody translated", Messages.get("Something nobody translated"));
        assertEquals("", Messages.get(""));
    }

    @Test
    public void testEnglish() {
        locale.setDefault("en");
        assertEquals("Refresh", Messages.get("Refresh"));
        assertEquals("Delete", Messages.get("Delete"));
    }

    @Test
    public void testLanguageVariable() {
        final java.util.function.Predicate<String> installed = l -> java.util.Arrays.asList("de", "fr", "pt_BR").contains(l);
        assertNull(Bootstrap.language(null, installed));
        assertNull(Bootstrap.language("", installed));
        assertNull(Bootstrap.language("C", installed));
        assertEquals("de", Bootstrap.language("de", installed));
        assertEquals("de", Bootstrap.language("de_CH.UTF-8", installed));
        assertEquals("fr", Bootstrap.language("xx:fr:de", installed));
        assertEquals("pt_BR", Bootstrap.language("pt_BR", installed));
        assertNull(Bootstrap.language("xx:yy", installed));
    }
}
