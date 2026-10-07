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
import ch.cyberduck.core.local.RevealService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Show a file in the file manager. The standard way to ask the desktop for this is to open the folder containing the
 * file, because the freedesktop specification has no way to select a file.
 */
public class XdgOpenRevealService implements RevealService {
    private static final Logger log = LogManager.getLogger(XdgOpenRevealService.class);

    @Override
    public boolean reveal(final Local file, final boolean select) {
        final String folder = file.isDirectory() ? file.getAbsolute() : file.getParent().getAbsolute();
        try {
            new ProcessBuilder("xdg-open", folder).inheritIO().start();
            return true;
        }
        catch(IOException e) {
            log.warn("Failure opening folder {} with xdg-open. {}", folder, e.getMessage());
            return false;
        }
    }
}
