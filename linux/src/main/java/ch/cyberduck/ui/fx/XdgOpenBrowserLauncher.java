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

import ch.cyberduck.core.local.BrowserLauncher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Open URLs with the default browser of the desktop through {@code xdg-open}. Avoids the AWT desktop API which
 * loads a second GUI toolkit next to JavaFX.
 */
public class XdgOpenBrowserLauncher implements BrowserLauncher {
    private static final Logger log = LogManager.getLogger(XdgOpenBrowserLauncher.class);

    @Override
    public boolean open(final String url) {
        try {
            new ProcessBuilder("xdg-open", url).inheritIO().start();
            return true;
        }
        catch(IOException e) {
            log.warn("Failure opening URL {} with xdg-open. {}", url, e.getMessage());
            return false;
        }
    }
}
