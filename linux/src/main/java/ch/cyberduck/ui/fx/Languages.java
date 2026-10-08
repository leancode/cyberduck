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

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The languages that the program has translations for
 */
public final class Languages {

    /**
     * Name of the preference with the language that overrides the one of the desktop session
     */
    public static final String PROPERTY = "linux.language";

    private Languages() {
        //
    }

    /**
     * @param resources Folder with the folders such as de.lproj and pt_BR.lproj
     * @return The names of the languages, such as de and pt_BR, in the order of their names in their own language
     */
    public static List<String> available(final File resources) {
        final List<String> languages = new ArrayList<>();
        final File[] folders = resources.listFiles((dir, name) -> name.endsWith(".lproj"));
        if(folders != null) {
            for(File folder : folders) {
                final String name = folder.getName().substring(0, folder.getName().length() - ".lproj".length());
                if(folder.isDirectory() && !"Base".equals(name) && !name.isEmpty()) {
                    languages.add(name);
                }
            }
        }
        languages.sort(Comparator.comparing(Languages::name, String.CASE_INSENSITIVE_ORDER));
        return languages;
    }

    /**
     * @param language Name of a language such as de or pt_BR
     * @return The name of the language in that language, for example Deutsch
     */
    public static String name(final String language) {
        final Locale locale = Locale.forLanguageTag(language.replace('_', '-'));
        final String name = locale.getDisplayName(locale);
        return name.isEmpty() ? language : name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }
}
