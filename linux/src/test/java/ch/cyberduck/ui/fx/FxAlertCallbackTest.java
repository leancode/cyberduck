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
import ch.cyberduck.core.exception.ConnectionCanceledException;
import ch.cyberduck.core.exception.LoginCanceledException;
import ch.cyberduck.core.exception.NotfoundException;
import ch.cyberduck.core.sftp.SFTPProtocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FxAlertCallbackTest {

    private final Host host = new Host(new SFTPProtocol(), "example.net");

    @Test
    public void testFailureIsShownAndNotRepeated() {
        final FakeDialogService dialogs = new FakeDialogService();
        assertFalse(new FxAlertCallback(dialogs).alert(host, new NotfoundException("Cannot read file", new RuntimeException("No such file"))));
        assertEquals(1, dialogs.errors.size());
        assertTrue(dialogs.errors.get(0).contains("Cannot read file"));
    }

    @Test
    public void testCancelledByUserIsNotShown() {
        final FakeDialogService dialogs = new FakeDialogService();
        assertFalse(new FxAlertCallback(dialogs).alert(host, new ConnectionCanceledException()));
        assertFalse(new FxAlertCallback(dialogs).alert(host, new LoginCanceledException()));
        assertTrue(dialogs.errors.isEmpty());
    }
}
