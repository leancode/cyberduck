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

import ch.cyberduck.core.StringAppender;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.transfer.TransferErrorCallback;
import ch.cyberduck.core.transfer.TransferItem;
import ch.cyberduck.core.transfer.TransferStatus;

/**
 * Ask whether to continue with the remaining files after a file of a transfer failed
 */
public class FxTransferErrorCallback implements TransferErrorCallback {

    private final DialogService dialogs;

    /**
     * Created by the core with the controller
     */
    public FxTransferErrorCallback(final FxController controller) {
        this(new FxDialogService(controller));
    }

    FxTransferErrorCallback(final DialogService dialogs) {
        this.dialogs = dialogs;
    }

    @Override
    public boolean prompt(final TransferItem item, final TransferStatus status, final BackgroundException failure, final int pending) throws BackgroundException {
        final String message = new StringAppender().append(failure.getDetail()).toString();
        if(dialogs.confirm(failure.getMessage(), pending > 0 ? String.format("%s%n%n%d more files remain.", message, pending) : message,
            "Continue", "Cancel", false).accepted()) {
            return true;
        }
        throw failure;
    }
}
