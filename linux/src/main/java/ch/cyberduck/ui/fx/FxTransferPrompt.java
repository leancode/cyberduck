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

import ch.cyberduck.core.Path;
import ch.cyberduck.core.pool.SessionPool;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.TransferAction;
import ch.cyberduck.core.transfer.TransferItem;
import ch.cyberduck.core.transfer.TransferPrompt;

import java.util.ArrayList;
import java.util.List;

/**
 * Ask what to do when a file already exists at the destination
 */
public class FxTransferPrompt implements TransferPrompt {

    private final FxController controller;
    private final DialogService dialogs;
    private final Transfer.Type type;

    /**
     * Created by the core for every transfer
     */
    public FxTransferPrompt(final FxController controller, final Transfer transfer, final SessionPool source, final SessionPool destination) {
        this(controller, new FxDialogService(controller), transfer.getType());
    }

    FxTransferPrompt(final FxController controller, final DialogService dialogs, final Transfer.Type type) {
        this.controller = controller;
        this.dialogs = dialogs;
        this.type = type;
    }

    @Override
    public TransferAction prompt(final TransferItem item) {
        final String name;
        switch(type) {
            case download:
                if(item.local.isDirectory()) {
                    // Merge folders
                    return TransferAction.overwrite;
                }
                name = item.local.getAbsolute();
                break;
            default:
                name = item.remote.getAbsolute();
        }
        final List<TransferAction> actions = new ArrayList<>(TransferAction.forTransfer(type));
        final TransferAction selected = dialogs.action("File exists",
            String.format("The file %s already exists. Choose what action to take.", name), actions);
        return null == selected ? TransferAction.cancel : selected;
    }

    @Override
    public boolean isSelected(final TransferItem file) {
        return true;
    }

    @Override
    public void message(final String message) {
        controller.message(message);
    }
}
