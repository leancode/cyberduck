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

import ch.cyberduck.core.BookmarkCollection;
import ch.cyberduck.core.Credentials;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.Local;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.sftp.SFTPProtocol;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Bookmarks are written as property lists. Proves that this works on Linux and that a second start reads them back.
 */
public class BookmarkPersistenceTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final SFTPProtocol sftp = new SFTPProtocol() {
        @Override
        public boolean isEnabled() {
            return true;
        }
    };

    private String monitor;

    @Before
    public void register() {
        // The reader looks up the protocol of a bookmark in the global factory
        ProtocolFactory.get().register(sftp);
        // The folder monitor re-reads files from a second thread. When a file is deleted right after it was written, a late
        // event can add the bookmark again and write the file back. These tests prove the files, not the monitor.
        monitor = PreferencesFactory.get().getProperty("bookmarks.folder.monitor");
        PreferencesFactory.get().setProperty("bookmarks.folder.monitor", false);
    }

    @After
    public void restore() {
        if(monitor == null) {
            PreferencesFactory.get().deleteProperty("bookmarks.folder.monitor");
        }
        else {
            PreferencesFactory.get().setProperty("bookmarks.folder.monitor", monitor);
        }
    }

    private Local directory() {
        return new Local(folder.getRoot().getAbsolutePath());
    }

    @Test
    public void testSaveAndLoad() throws Exception {
        final BookmarkCollection first = new BookmarkCollection(directory());
        first.load();
        final Host bookmark = new Host(sftp, "example.net", 2222, "/home/alice", new Credentials("alice"));
        bookmark.setNickname("Example");
        first.add(bookmark);
        assertTrue("One file per bookmark", new Local(folder.getRoot().getAbsolutePath(), String.format("%s.duck", bookmark.getUuid())).exists());

        // Next start
        final BookmarkCollection second = new BookmarkCollection(directory());
        second.load();
        assertEquals(1, second.size());
        final Host read = second.get(0);
        assertEquals(bookmark.getUuid(), read.getUuid());
        assertEquals("Example", read.getNickname());
        assertEquals("example.net", read.getHostname());
        assertEquals(2222, read.getPort());
        assertEquals("alice", read.getCredentials().getUsername());
        assertEquals("/home/alice", read.getDefaultPath());
        assertEquals(sftp.getIdentifier(), read.getProtocol().getIdentifier());
    }

    @Test
    public void testChangeIsSaved() throws Exception {
        final BookmarkCollection first = new BookmarkCollection(directory());
        first.load();
        final Host bookmark = new Host(sftp, "example.net");
        first.add(bookmark);
        bookmark.setNickname("Renamed");
        first.collectionItemChanged(bookmark);

        final BookmarkCollection second = new BookmarkCollection(directory());
        second.load();
        assertEquals("Renamed", second.get(0).getNickname());
    }

    @Test
    public void testRemoveDeletesFile() throws Exception {
        final BookmarkCollection first = new BookmarkCollection(directory());
        first.load();
        final Host bookmark = new Host(sftp, "example.net");
        first.add(bookmark);
        final Local file = new Local(folder.getRoot().getAbsolutePath(), String.format("%s.duck", bookmark.getUuid()));
        assertTrue(file.exists());
        first.remove(bookmark);
        assertFalse(file.exists());

        final BookmarkCollection second = new BookmarkCollection(directory());
        second.load();
        assertEquals(0, second.size());
    }

    @Test
    public void testPasswordIsNotWrittenToBookmark() throws Exception {
        final BookmarkCollection collection = new BookmarkCollection(directory());
        collection.load();
        final Host bookmark = new Host(sftp, "example.net", new Credentials("alice", "s3cret-password"));
        collection.add(bookmark);
        final String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(folder.getRoot().getAbsolutePath(), String.format("%s.duck", bookmark.getUuid()))));
        assertTrue(content.contains("alice"));
        assertFalse(content.contains("s3cret-password"));
    }
}
