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

import ch.cyberduck.core.Credentials;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.LoginOptions;
import ch.cyberduck.core.transfer.TransferAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Answers dialogs with canned values and records what was asked
 */
public class FakeDialogService implements DialogService {

    public Credentials credentials;
    public Confirmation confirmation = Confirmation.YES;
    public TransferAction action;
    public final List<TransferAction> offered = new ArrayList<>();

    public int credentialRequests;
    public final List<String> titles = new ArrayList<>();
    public final List<String> messages = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();

    @Override
    public Credentials credentials(final Host bookmark, final String username, final String title, final String reason, final LoginOptions options) {
        credentialRequests++;
        titles.add(title);
        return credentials;
    }

    @Override
    public Confirmation confirm(final String title, final String message, final String defaultButton, final String cancelButton, final boolean suppressible) {
        titles.add(title);
        messages.add(message);
        return confirmation;
    }

    @Override
    public TransferAction action(final String title, final String message, final List<TransferAction> actions) {
        titles.add(title);
        messages.add(message);
        offered.addAll(actions);
        return action;
    }

    @Override
    public void error(final String title, final String message) {
        errors.add(String.format("%s|%s", title, message));
    }
}
