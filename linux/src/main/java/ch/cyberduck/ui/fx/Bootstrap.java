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
import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.TransferCollection;
import ch.cyberduck.core.exception.AccessDeniedException;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.serviceloader.AutoServiceLoaderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
        final ProtocolFactory protocols = ProtocolFactory.get();
        for(Protocol p : AutoServiceLoaderFactory.<Protocol>get().load(Protocol.class)) {
            protocols.register(p);
        }
        // Profiles in the application resources folder and in the application support folder
        protocols.load();
        log.info("Registered {} protocols", protocols.find().size());
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
