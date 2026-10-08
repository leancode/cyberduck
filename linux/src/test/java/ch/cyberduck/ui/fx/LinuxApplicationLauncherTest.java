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
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LinuxApplicationLauncherTest {

    @Test
    public void testTokenize() {
        assertEquals(Arrays.asList("code", "--wait"), LinuxApplicationLauncher.tokenize("code --wait"));
        assertEquals(Arrays.asList("/opt/My Editor/edit", "-x"), LinuxApplicationLauncher.tokenize("\"/opt/My Editor/edit\" -x"));
        assertEquals(Arrays.asList("/opt/My Editor/edit"), LinuxApplicationLauncher.tokenize("/opt/My\\ Editor/edit"));
        assertEquals(Arrays.asList("a", "", "b"), LinuxApplicationLauncher.tokenize("a '' b"));
        assertEquals(List.of(), LinuxApplicationLauncher.tokenize("   "));
    }
}
