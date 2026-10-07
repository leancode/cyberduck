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
import ch.cyberduck.core.LocalFactory;
import ch.cyberduck.core.preferences.ApplicationResourcesFinder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.URI;

/**
 * Resources are installed next to the application classes. This is the folder containing the application jar or
 * the compiled classes when started from the build tree.
 */
public class LinuxApplicationResourcesFinder implements ApplicationResourcesFinder {
    private static final Logger log = LogManager.getLogger(LinuxApplicationResourcesFinder.class);

    @Override
    public Local find() {
        final String current = new File(URI.create(LinuxApplicationResourcesFinder.class.getProtectionDomain().getCodeSource().getLocation().toString())).getPath();
        final Local parent = LocalFactory.get(current).getParent();
        log.debug("Use folder {} for application resources directory", parent);
        return parent;
    }
}
