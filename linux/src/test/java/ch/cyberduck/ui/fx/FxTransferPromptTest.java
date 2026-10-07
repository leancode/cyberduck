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
import ch.cyberduck.core.Path;
import ch.cyberduck.core.exception.ConnectionRefusedException;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.TransferAction;
import ch.cyberduck.core.transfer.TransferItem;
import ch.cyberduck.core.transfer.TransferStatus;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FxTransferPromptTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Path remote = new Path("/remote/file.bin", EnumSet.of(Path.Type.file));

    @Test
    public void testDownloadExistingFileAsksWhichAction() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        dialogs.action = TransferAction.resume;
        final Local local = new Local(folder.newFile("file.bin").getAbsolutePath());
        final TransferAction action = new FxTransferPrompt(new FxController(), dialogs, Transfer.Type.download).prompt(new TransferItem(remote, local));
        assertSame(TransferAction.resume, action);
        assertTrue(dialogs.messages.get(0).contains(local.getAbsolute()));
        assertTrue(dialogs.offered.contains(TransferAction.overwrite));
        assertTrue(dialogs.offered.contains(TransferAction.resume));
        assertTrue(dialogs.offered.contains(TransferAction.skip));
    }

    @Test
    public void testSynchronizeAsksForTheDirection() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        dialogs.action = TransferAction.mirror;
        final Local local = new Local(folder.getRoot().getAbsolutePath());
        final TransferAction action = new FxTransferPrompt(new FxController(), dialogs, Transfer.Type.sync).prompt(new TransferItem(remote, local));
        assertSame(TransferAction.mirror, action);
        assertEquals("Synchronize", dialogs.titles.get(0));
        assertTrue(dialogs.messages.get(0).contains(local.getAbsolute()));
        assertEquals(java.util.List.of(TransferAction.download, TransferAction.upload, TransferAction.mirror), dialogs.offered);
    }

    @Test
    public void testCancelledDialogCancelsTransfer() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        final Local local = new Local(folder.newFile("file.bin").getAbsolutePath());
        assertSame(TransferAction.cancel, new FxTransferPrompt(new FxController(), dialogs, Transfer.Type.download).prompt(new TransferItem(remote, local)));
    }

    @Test
    public void testExistingFolderIsMergedWithoutAsking() {
        final FakeDialogService dialogs = new FakeDialogService();
        final Local local = new Local(folder.getRoot().getAbsolutePath());
        assertSame(TransferAction.overwrite, new FxTransferPrompt(new FxController(), dialogs, Transfer.Type.download).prompt(new TransferItem(remote, local)));
        assertTrue(dialogs.titles.isEmpty());
    }

    @Test
    public void testUploadNamesRemoteFile() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        dialogs.action = TransferAction.overwrite;
        new FxTransferPrompt(new FxController(), dialogs, Transfer.Type.upload).prompt(new TransferItem(remote, new Local(folder.newFile("file.bin").getAbsolutePath())));
        assertTrue(dialogs.messages.get(0).contains("/remote/file.bin"));
    }

    @Test
    public void testErrorContinues() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        assertTrue(new FxTransferErrorCallback(dialogs).prompt(new TransferItem(remote), new TransferStatus(), new ConnectionRefusedException("Connection failed", new RuntimeException("refused")), 3));
        assertTrue(dialogs.messages.get(0).contains("3 more files remain"));
    }

    @Test
    public void testErrorCancelledRethrowsFailure() {
        final FakeDialogService dialogs = new FakeDialogService();
        dialogs.confirmation = DialogService.Confirmation.NO;
        final BackgroundException failure = new ConnectionRefusedException("Connection failed", new RuntimeException("refused"));
        try {
            new FxTransferErrorCallback(dialogs).prompt(new TransferItem(remote), new TransferStatus(), failure, 0);
            fail("Expected failure");
        }
        catch(BackgroundException e) {
            assertEquals(failure, e);
        }
    }
}
