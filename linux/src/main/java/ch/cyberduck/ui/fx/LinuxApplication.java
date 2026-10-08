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

/**
 * An application that is started with a command. The base class changes the identifier to lower case, as it does for
 * the identifiers of bundles on macOS, which would break a command with a path that has capitals in it.
 */
public class LinuxApplication extends Application {
    private final String command;

    public LinuxApplication(final String command, final String name) {
        super(command, name);
        this.command = command;
    }

    /**
     * @return The command that starts the application, in the case it was given
     */
    @Override
    public String getIdentifier() {
        return command;
    }
}
