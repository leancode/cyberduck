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
import ch.cyberduck.core.DisabledHostKeyCallback;
import ch.cyberduck.core.DisabledListProgressListener;
import ch.cyberduck.core.DisabledPasswordStore;
import ch.cyberduck.core.DisabledTranscriptListener;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathCache;
import ch.cyberduck.core.ProgressListener;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.SessionPoolFactory;
import ch.cyberduck.core.ConnectionCallback;
import ch.cyberduck.core.features.Location;
import ch.cyberduck.core.features.Read;
import ch.cyberduck.core.features.Write;
import ch.cyberduck.core.nio.LocalProtocol;
import ch.cyberduck.core.pool.SessionPool;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.threading.WorkerBackgroundAction;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.core.vault.RegistryVaultLoader;
import ch.cyberduck.core.vault.VaultCredentials;
import ch.cyberduck.core.vault.VaultVersion;
import ch.cyberduck.core.worker.CreateVaultWorker;
import ch.cyberduck.core.worker.LoadVaultWorker;
import ch.cyberduck.core.worker.ListWorker;
import ch.cyberduck.core.worker.LockVaultWorker;
import ch.cyberduck.core.worker.MountWorker;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A Cryptomator vault on the local filesystem with the workers of the browser window. Uploads a file into the vault,
 * lists it through the vault and looks at what is on disk.
 */
public class VaultIntegrationTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final LocalProtocol local = new LocalProtocol() {
        @Override
        public boolean isEnabled() {
            return true;
        }
    };

    @Before
    public void preferences() throws Exception {
        // The callbacks show their dialogs on the JavaFX application thread
        FxToolkit.init();
        final LinuxApplicationPreferences preferences = new LinuxApplicationPreferences(folder.newFolder().toPath().resolve("cyberduck.properties"));
        PreferencesFactory.set(preferences);
        ProtocolFactory.get().register(local);
    }

    private List<String> names(final File directory) throws Exception {
        try(Stream<java.nio.file.Path> walk = Files.walk(directory.toPath())) {
            return walk.filter(p -> !p.equals(directory.toPath())).map(p -> directory.toPath().relativize(p).toString()).collect(Collectors.toList());
        }
    }

    @Test(timeout = 120000L)
    public void testCreateWriteListLockUnlock() throws Exception {
        final File root = folder.newFolder();
        final FakeDialogService dialogs = new FakeDialogService();
        dialogs.credentials = new Credentials(null, "vault passphrase").setSaved(false);
        final HeadlessController controller = new HeadlessController();
        final Host host = new Host(local, "localhost").setDefaultPath(root.getAbsolutePath());
        final SessionPool pool = SessionPoolFactory.create(controller, host, new DisabledPasswordStore(),
            new FxLoginCallback(new FxController(), dialogs), new DisabledHostKeyCallback(),
            ProgressListener.noop, new DisabledTranscriptListener(), SessionPoolFactory.Usage.browser);
        try {
            final PathCache cache = new PathCache(100);
            final Path home = controller.background(new WorkerBackgroundAction<>(controller, pool,
                new MountWorker(host, cache, new DisabledListProgressListener()))).get();
            final Path vault = new Path(home, "secret", EnumSet.of(Path.Type.directory));

            // Create
            controller.background(new WorkerBackgroundAction<>(controller, pool,
                new CreateVaultWorker(Location.unknown.getIdentifier(), vault, new VaultCredentials("vault passphrase").setSaved(false),
                    new VaultVersion(VaultVersion.Type.valueOf(PreferencesFactory.get().getProperty("cryptomator.vault.default")))))).get();
            final List<String> created = names(new File(root, "secret"));
            assertTrue(created.toString(), created.stream().anyMatch(n -> n.startsWith("vault.cryptomator") || n.startsWith("masterkey")));

            // Unlock, then write a file with a name and a content that must not be on disk in clear text
            controller.background(new WorkerBackgroundAction<>(controller, pool,
                new LoadVaultWorker(new RegistryVaultLoader(pool.getVaultRegistry(), new FxPasswordCallback(dialogs)), vault))).get();
            assertTrue(pool.getVaultRegistry().contains(vault));
            final Path file = new Path(vault, "invoice-2026.txt", EnumSet.of(Path.Type.file));
            final byte[] content = "the amount due is 1234 francs".getBytes(StandardCharsets.UTF_8);
            final ch.cyberduck.core.Session<?> session = pool.borrow(ch.cyberduck.core.threading.BackgroundActionState.running);
            try {
                // The features of the session are the ones that encrypt, the underlying ones are not
                final TransferStatus status = new TransferStatus().setLength(content.length);
                final Write<?> write = session.getFeature(Write.class);
                try(OutputStream out = write.write(file, status, ConnectionCallback.noop)) {
                    out.write(content);
                }
                final Read read = session.getFeature(Read.class);
                try(InputStream in = read.read(file, new TransferStatus().setLength(content.length), ConnectionCallback.noop)) {
                    assertEquals(new String(content, StandardCharsets.UTF_8), new String(IOUtils.toByteArray(in), StandardCharsets.UTF_8));
                }
            }
            finally {
                pool.release(session, null);
            }

            // The listing through the vault shows the real name
            controller.background(new WorkerBackgroundAction<>(controller, pool,
                new ListWorker(cache, vault, new DisabledListProgressListener()))).get();
            final AttributedList<Path> listing = cache.get(vault);
            assertTrue(String.valueOf(listing.toList()), listing.toList().stream().anyMatch(p -> p.getName().equals("invoice-2026.txt")));

            // On disk there is neither the name nor the text
            final List<String> raw = names(new File(root, "secret"));
            assertFalse(raw.toString(), raw.stream().anyMatch(n -> n.contains("invoice")));
            try(Stream<java.nio.file.Path> walk = Files.walk(new File(root, "secret").toPath())) {
                for(java.nio.file.Path p : walk.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    assertFalse(p.toString(), new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1).contains("1234 francs"));
                }
            }

            // Lock: the encrypted names show again
            controller.background(new WorkerBackgroundAction<>(controller, pool, new LockVaultWorker(pool.getVaultRegistry(), vault))).get();
            assertFalse(pool.getVaultRegistry().contains(vault));
        }
        finally {
            pool.shutdown();
        }
    }
}
