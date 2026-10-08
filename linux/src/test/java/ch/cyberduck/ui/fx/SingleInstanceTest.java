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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SingleInstanceTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testPassesLinksToTheRunningApplication() throws Exception {
        final Path socket = folder.getRoot().toPath().resolve("cyberduck.sock");
        final List<String> received = new CopyOnWriteArrayList<>();
        try(SingleInstance instance = SingleInstance.listen(socket, received::add)) {
            assertNotNull(instance);
            assertTrue(SingleInstance.forward(socket, Arrays.asList("io.cyberduck:oauth?code=1&state=2", "sftp://example.net")));
            assertEquals(Arrays.asList("io.cyberduck:oauth?code=1&state=2", "sftp://example.net"), received);
        }
    }

    @Test
    public void testStartWithoutLinkBringsTheWindowToTheFront() throws Exception {
        final Path socket = folder.getRoot().toPath().resolve("cyberduck.sock");
        final List<String> received = new CopyOnWriteArrayList<>();
        try(SingleInstance instance = SingleInstance.listen(socket, received::add)) {
            assertNotNull(instance);
            assertTrue(SingleInstance.forward(socket, Collections.emptyList()));
            assertEquals(Collections.singletonList(SingleInstance.ACTIVATE), received);
        }
    }

    @Test
    public void testNothingRunning() {
        assertFalse(SingleInstance.forward(folder.getRoot().toPath().resolve("missing.sock"), Collections.singletonList("sftp://example.net")));
    }

    @Test
    public void testLeftBehindSocketIsReplaced() throws Exception {
        final Path socket = folder.getRoot().toPath().resolve("cyberduck.sock");
        // Of an application that was killed
        Files.createFile(socket);
        assertFalse(SingleInstance.forward(socket, Collections.singletonList("sftp://example.net")));
        final List<String> received = new CopyOnWriteArrayList<>();
        try(SingleInstance instance = SingleInstance.listen(socket, received::add)) {
            assertNotNull(instance);
            assertTrue(SingleInstance.forward(socket, Collections.singletonList("sftp://example.net")));
            assertEquals(1, received.size());
        }
    }

    @Test
    public void testOnlyOneApplicationListens() throws Exception {
        final Path socket = folder.getRoot().toPath().resolve("cyberduck.sock");
        try(SingleInstance first = SingleInstance.listen(socket, line -> {
        })) {
            assertNotNull(first);
            assertNull(SingleInstance.listen(socket, line -> {
            }));
            // The first one is still reachable
            assertTrue(SingleInstance.forward(socket, Collections.singletonList("sftp://example.net")));
        }
    }

    @Test
    public void testClosingRemovesTheSocket() throws Exception {
        final Path socket = folder.getRoot().toPath().resolve("cyberduck.sock");
        SingleInstance.listen(socket, line -> {
        }).close();
        assertFalse(Files.exists(socket));
    }
}
