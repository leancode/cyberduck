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

import ch.cyberduck.core.BookmarkCollection;
import ch.cyberduck.core.LocaleFactory;
import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.TransferCollection;
import ch.cyberduck.core.exception.AccessDeniedException;
import ch.cyberduck.core.preferences.ApplicationResourcesFinderFactory;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.serviceloader.AutoServiceLoaderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.function.Predicate;

/**
 * Application startup in the order required by the core: preferences first, because the factories are resolved
 * from them, then protocols and profiles, then bookmarks.
 */
public final class Bootstrap {
    private static final Logger log = LogManager.getLogger(Bootstrap.class);

    private Bootstrap() {
        //
    }

    /**
     * Register preferences, protocols and bundled and user profiles. Does not need a display.
     */
    public static void initialize() {
        PreferencesFactory.set(new LinuxApplicationPreferences());
        final File resources = new File(ApplicationResourcesFinderFactory.get().find().getAbsolute());
        final String language = language(System.getenv("LANGUAGE"), l -> new File(resources, String.format("%s.lproj", l)).isDirectory());
        if(language != null) {
            log.info("Use language {}", language);
            LocaleFactory.get().setDefault(language);
        }
        final ProtocolFactory protocols = ProtocolFactory.get();
        for(Protocol p : AutoServiceLoaderFactory.<Protocol>get().load(Protocol.class)) {
            protocols.register(p);
        }
        // Profiles in the application resources folder and in the application support folder
        protocols.load();
        log.info("Registered {} protocols", protocols.find().size());
    }

    /**
     * The language list of the desktop session in the format of gettext, such as <code>de:en</code> or
     * <code>pt_BR</code>. Without it the language is the one of the locale of the process.
     *
     * @param variable  Value of the environment variable LANGUAGE
     * @param available Tests that translations exist for a language
     * @return First language with translations or null to keep the language of the process
     */
    public static String language(final String variable, final Predicate<String> available) {
        if(null == variable) {
            return null;
        }
        for(String entry : variable.split(":")) {
            // Without the encoding and the modifier
            final String name = entry.trim().replaceAll("[.@].*$", "");
            if(name.isEmpty() || "C".equals(name) || "POSIX".equals(name)) {
                continue;
            }
            if(available.test(name)) {
                return name;
            }
            final String base = name.replaceAll("_.*$", "");
            if(!base.equals(name) && available.test(base)) {
                return base;
            }
        }
        return null;
    }

    /**
     * Read the bookmarks and the list of transfers from the application support folder.
     */
    public static void loadBookmarks() {
        final TransferCollection transfers = TransferCollection.defaultCollection();
        try {
            transfers.load();
            log.info("Loaded {} transfers", transfers.size());
        }
        catch(AccessDeniedException e) {
            log.warn("Failure loading transfers. {}", e.getMessage());
        }
        final BookmarkCollection bookmarks = BookmarkCollection.defaultCollection();
        try {
            bookmarks.load();
            log.info("Loaded {} bookmarks", bookmarks.size());
        }
        catch(AccessDeniedException e) {
            // Continue without bookmarks
            log.warn("Failure loading bookmarks. {}", e.getMessage());
        }
    }
}
