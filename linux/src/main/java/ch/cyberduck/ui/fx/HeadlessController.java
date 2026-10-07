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

import ch.cyberduck.core.AbstractController;
import ch.cyberduck.core.threading.MainAction;

/**
 * Controller without a user interface. Main actions run on the calling thread. Used by the smoke tests that must
 * run without a display.
 */
public class HeadlessController extends AbstractController {

    @Override
    public void invoke(final MainAction runnable, final boolean wait) {
        if(runnable.isValid()) {
            runnable.run();
        }
    }
}
