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
import ch.cyberduck.core.local.ApplicationFinderFactory;
import ch.cyberduck.core.preferences.PreferencesFactory;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Programs that show the differences of two files, and the one that the user chose
 */
public final class CompareTools {

    /**
     * Name of the preference with the command of the program
     */
    public static final String PROPERTY = "linux.compare.tool";

    private static final String[][] KNOWN = {
        {"meld", "Meld"},
        {"kdiff3", "KDiff3"},
        {"kompare", "Kompare"},
        {"diffuse", "Diffuse"},
        {"xxdiff", "xxdiff"},
        {"code --diff", "Visual Studio Code"},
        {"diffmerge", "DiffMerge"},
        {"bcompare", "Beyond Compare"},
    };

    private CompareTools() {
        //
    }

    /**
     * @return The programs for comparing that are installed
     */
    public static List<Application> installed() {
        return installed(ApplicationFinderFactory.get());
    }

    static List<Application> installed(final ApplicationFinder finder) {
        final List<Application> installed = new ArrayList<>();
        for(String[] tool : KNOWN) {
            final Application application = new LinuxApplication(tool[0], tool[1]);
            if(finder.isInstalled(application)) {
                installed.add(application);
            }
        }
        return installed;
    }

    /**
     * @return The program that was chosen in the preferences or else the first one that is installed, or
     * {@link Application#notfound} when there is none
     */
    public static Application preferred() {
        return preferred(ApplicationFinderFactory.get());
    }

    static Application preferred(final ApplicationFinder finder) {
        final String chosen = PreferencesFactory.get().getProperty(PROPERTY);
        if(StringUtils.isNotBlank(chosen)) {
            final Application application = finder.getDescription(chosen);
            if(finder.isInstalled(application)) {
                return application;
            }
        }
        final List<Application> installed = installed(finder);
        return installed.isEmpty() ? Application.notfound : installed.get(0);
    }
}
