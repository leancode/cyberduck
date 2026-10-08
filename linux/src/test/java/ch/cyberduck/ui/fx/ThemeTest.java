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

import javafx.application.ColorScheme;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThemeTest {

    @Test
    public void testChoiceBeatsTheSystem() {
        assertTrue(Theme.isDark("dark", ColorScheme.LIGHT));
        assertFalse(Theme.isDark("light", ColorScheme.DARK));
    }

    @Test
    public void testSystemDecidesOtherwise() {
        assertTrue(Theme.isDark("system", ColorScheme.DARK));
        assertTrue(Theme.isDark(null, ColorScheme.DARK));
        assertFalse(Theme.isDark("", ColorScheme.LIGHT));
        assertFalse(Theme.isDark("system", ColorScheme.LIGHT));
    }
}
