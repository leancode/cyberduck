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

import ch.cyberduck.core.Host;
import ch.cyberduck.core.StringAppender;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.threading.AlertCallback;
import ch.cyberduck.core.threading.DefaultFailureDiagnostics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Show failures of background actions in a dialog
 */
public class FxAlertCallback implements AlertCallback {
    private static final Logger log = LogManager.getLogger(FxAlertCallback.class);

    private final DialogService dialogs;

    /**
     * Created by the core with the controller of the window
     */
    public FxAlertCallback(final FxController controller) {
        this(new FxDialogService(controller));
    }

    FxAlertCallback(final DialogService dialogs) {
        this.dialogs = dialogs;
    }

    @Override
    public boolean alert(final Host host, final BackgroundException failure) {
        log.warn("Notify for failure {}", failure.toString());
        switch(new DefaultFailureDiagnostics().determine(failure)) {
            case cancel:
            case skip:
                // Intentional, the user knows
                break;
            default:
                dialogs.error(failure.getMessage(), new StringAppender().append(failure.getDetail()).toString());
        }
        // Never repeat
        return false;
    }
}
