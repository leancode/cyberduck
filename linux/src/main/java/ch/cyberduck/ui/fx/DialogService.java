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

/**
 * Questions asked to the user. Separated from the callbacks of the core so that they can be tested without a window.
 * Implementations may be called from any thread and block until the user has answered.
 */
public interface DialogService {

    /**
     * @param bookmark Connection the credentials are for
     * @param username Suggested username or null
     * @param title    Short summary
     * @param reason   Explanation, for example why the previous login failed
     * @param options  Which fields to show
     * @return Input of the user or null if cancelled
     */
    Credentials credentials(Host bookmark, String username, String title, String reason, LoginOptions options);

    /**
     * @param title         Short summary
     * @param message       Details
     * @param defaultButton Label to continue
     * @param cancelButton  Label to cancel
     * @param suppressible  Offer to never ask again
     */
    Confirmation confirm(String title, String message, String defaultButton, String cancelButton, boolean suppressible);

    /**
     * Report a failure and wait until it has been acknowledged
     */
    void error(String title, String message);

    /**
     * @param accepted   True if the user chose to continue
     * @param suppressed True if the user asked never to be asked again
     */
    record Confirmation(boolean accepted, boolean suppressed) {
        public static final Confirmation YES = new Confirmation(true, false);
        public static final Confirmation NO = new Confirmation(false, false);
    }
}
