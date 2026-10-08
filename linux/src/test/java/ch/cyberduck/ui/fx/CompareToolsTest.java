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
import ch.cyberduck.core.local.ApplicationFinder;
import ch.cyberduck.core.preferences.PreferencesFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class CompareToolsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setup() throws Exception {
        PreferencesFactory.set(new LinuxApplicationPreferences(folder.newFolder().toPath().resolve("cyberduck.properties")));
    }

    /**
     * Programs that are installed, by command
     */
    private static ApplicationFinder finder(final Set<String> installed) {
        return new ApplicationFinder() {
            @Override
            public List<Application> findAll(final String filename) {
                return List.of();
            }

            @Override
            public Application find(final String filename) {
                return Application.notfound;
            }

            @Override
            public boolean isInstalled(final Application application) {
                return application != null && installed.contains(application.getIdentifier());
            }

            @Override
            public Application getDescription(final String identifier) {
                return new LinuxApplication(identifier, identifier);
            }
        };
    }

    @Test
    public void testNoneInstalled() {
        assertEquals(Application.notfound, CompareTools.preferred(finder(Set.of())));
        assertEquals(List.of(), CompareTools.installed(finder(Set.of())));
    }

    @Test
    public void testFirstInstalledProgramIsUsed() {
        assertEquals("kdiff3", CompareTools.preferred(finder(Set.of("kompare", "kdiff3"))).getIdentifier());
        assertEquals(List.of("kdiff3", "kompare"), CompareTools.installed(finder(Set.of("kompare", "kdiff3"))).stream().map(Application::getIdentifier).toList());
    }

    @Test
    public void testChosenProgramWins() {
        PreferencesFactory.get().setProperty(CompareTools.PROPERTY, "/opt/Tools/mydiff");
        assertEquals("/opt/Tools/mydiff", CompareTools.preferred(finder(Set.of("meld", "/opt/Tools/mydiff"))).getIdentifier());
        // Not installed any more
        assertEquals("meld", CompareTools.preferred(finder(Set.of("meld"))).getIdentifier());
    }

    @Test
    public void testProgramWithAnArgument() {
        assertEquals("code --diff", CompareTools.preferred(finder(Set.of("code --diff"))).getIdentifier());
    }
}
