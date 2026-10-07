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

import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.ftp.FTPProtocol;
import ch.cyberduck.core.sftp.SFTPProtocol;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Changes values in the controls of the preferences window and reads the file that a next start loads.
 */
public class PreferencesControllerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final SFTPProtocol sftp = new SFTPProtocol() {
        @Override
        public boolean isEnabled() {
            return true;
        }
    };
    private final FTPProtocol ftp = new FTPProtocol() {
        @Override
        public boolean isEnabled() {
            return true;
        }
    };

    @Before
    public void setup() {
        FxToolkit.init();
        ProtocolFactory.get().register(sftp, ftp);
    }

    private static <T> T onFx(final Callable<T> callable) throws Exception {
        final FutureTask<T> task = new FutureTask<>(callable);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private Path file() throws Exception {
        return folder.getRoot().toPath().resolve("cyberduck.properties");
    }

    @Test
    public void testChangesArePersisted() throws Exception {
        final LinuxApplicationPreferences first = new LinuxApplicationPreferences(this.file());
        first.setFactories();
        first.setDefaults();
        first.load();
        final PreferencesController window = new PreferencesController(first);
        onFx(() -> {
            window.build();
            window.getTimeout().getValueFactory().setValue(45);
            window.getRetries().getValueFactory().setValue(3);
            window.getShowHidden().fire();
            window.getProxy().fire();
            window.getDownloadFolder().setText("/tmp/downloads");
            window.getDownloadFolder().fireEvent(new javafx.event.ActionEvent());
            window.getDownloadAction().setValue(window.getDownloadAction().getItems().stream().filter(c -> "skip".equals(c.getValue())).findFirst().orElseThrow());
            window.getUploadAction().setValue(window.getUploadAction().getItems().stream().filter(c -> "overwrite".equals(c.getValue())).findFirst().orElseThrow());
            window.getTransferType().setValue(window.getTransferType().getItems().stream().filter(c -> "browser".equals(c.getValue())).findFirst().orElseThrow());
            window.getProtocol().setValue(ftp);
            return null;
        });

        // The next start
        final LinuxApplicationPreferences second = new LinuxApplicationPreferences(this.file());
        second.setDefaults();
        second.load();
        assertEquals(45, second.getInteger("connection.timeout.seconds"));
        assertEquals(3, second.getInteger("connection.retry"));
        assertTrue(second.getBoolean("browser.showHidden"));
        assertEquals("/tmp/downloads", second.getProperty("queue.download.folder"));
        assertEquals("skip", second.getProperty("queue.download.action"));
        assertEquals("overwrite", second.getProperty("queue.upload.action"));
        assertEquals("browser", second.getProperty("queue.transfer.type"));
        assertEquals(ftp.getIdentifier(), second.getProperty("connection.protocol.default"));
        // The proxy setting is on by default and was switched off
        assertFalse(second.getBoolean("connection.proxy.enable"));

        // And the window shows the saved values
        final PreferencesController reopened = new PreferencesController(second);
        onFx(() -> {
            reopened.build();
            assertEquals(Integer.valueOf(45), reopened.getTimeout().getValue());
            assertEquals(Integer.valueOf(3), reopened.getRetries().getValue());
            assertTrue(reopened.getShowHidden().isSelected());
            assertFalse(reopened.getProxy().isSelected());
            assertEquals("/tmp/downloads", reopened.getDownloadFolder().getText());
            assertEquals("skip", reopened.getDownloadAction().getValue().getValue());
            assertEquals("browser", reopened.getTransferType().getValue().getValue());
            final Protocol selected = reopened.getProtocol().getValue();
            assertEquals(ftp.getIdentifier(), selected.getIdentifier());
            return null;
        });
    }

    @Test
    public void testClearingTheDownloadFolderRestoresTheDefault() throws Exception {
        final LinuxApplicationPreferences preferences = new LinuxApplicationPreferences(this.file());
        preferences.setDefaults();
        preferences.load();
        final String standard = preferences.getProperty("queue.download.folder");
        final PreferencesController window = new PreferencesController(preferences);
        onFx(() -> {
            window.build();
            window.getDownloadFolder().setText("/tmp/other");
            window.getDownloadFolder().fireEvent(new javafx.event.ActionEvent());
            window.getDownloadFolder().setText("");
            window.getDownloadFolder().fireEvent(new javafx.event.ActionEvent());
            return null;
        });
        final LinuxApplicationPreferences next = new LinuxApplicationPreferences(this.file());
        next.setDefaults();
        next.load();
        assertEquals(standard, next.getProperty("queue.download.folder"));
    }
}
