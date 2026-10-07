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

import ch.cyberduck.core.AttributedList;
import ch.cyberduck.core.Credentials;
import ch.cyberduck.core.DisabledPasswordStore;
import ch.cyberduck.core.DisabledTranscriptListener;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.Local;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathCache;
import ch.cyberduck.core.ProgressListener;
import ch.cyberduck.core.SessionPoolFactory;
import ch.cyberduck.core.pool.SessionPool;
import ch.cyberduck.core.sftp.SFTPProtocol;
import ch.cyberduck.core.threading.WorkerBackgroundAction;
import ch.cyberduck.core.worker.MountWorker;
import ch.cyberduck.test.TestcontainerTest;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Connect to a real SFTP server the way the browser window does: log in through the callbacks of the application,
 * trust the host key and list the home directory. The questions to the user are answered by a fake.
 * <p>
 * Needs Docker and is skipped when Docker is not available. Excluded with {@code -P no-testcontainers}.
 */
@Category(TestcontainerTest.class)
public class SFTPBrowserIntegrationTest {

    private static GenericContainer<?> container;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @BeforeClass
    public static void start() {
        Assume.assumeTrue("Docker is not available", DockerClientFactory.instance().isDockerAvailable());
        // User foo with password pass and a writable folder upload
        container = new GenericContainer<>("atmoz/sftp:alpine")
            .withExposedPorts(22)
            .withCommand("foo:pass:::upload")
            .waitingFor(Wait.forListeningPort());
        container.start();
    }

    @AfterClass
    public static void stop() {
        if(container != null) {
            container.stop();
        }
    }

    @Test(timeout = 120000L)
    public void testMountAndList() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        dialogs.credentials = new Credentials("foo", "pass");
        final HeadlessController controller = new HeadlessController();
        // Username known but no password, so the login callback has to ask
        final Host host = new Host(new SFTPProtocol(), container.getHost(), container.getMappedPort(22)).setCredentials(new Credentials("foo"));
        final SessionPool pool = SessionPoolFactory.create(controller, host, new DisabledPasswordStore(),
            new FxLoginCallback(new FxController(), dialogs),
            new FxHostKeyCallback(new Local(folder.newFile("known_hosts").getAbsolutePath()), dialogs),
            ProgressListener.noop, new DisabledTranscriptListener(), SessionPoolFactory.Usage.browser);
        try {
            final PathCache cache = new PathCache(100);
            final Path home = controller.background(new WorkerBackgroundAction<>(controller, pool,
                new MountWorker(host, cache, new DisabledListProgress()))).get();
            final AttributedList<Path> list = cache.get(home);
            assertTrue(String.format("Listing of %s: %s", home, list), list.toList().stream().anyMatch(p -> p.getName().equals("upload")));
            assertEquals("Asked for the password once", 1, dialogs.credentialRequests);
            assertTrue("Asked to trust the unknown host key", dialogs.titles.stream().anyMatch(t -> t.startsWith("Unknown fingerprint")));
        }
        finally {
            pool.shutdown();
        }
    }

    private static final class DisabledListProgress extends ch.cyberduck.core.DisabledListProgressListener {
    }
}
