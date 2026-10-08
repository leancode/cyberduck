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
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.ftp.FTPConnectMode;
import ch.cyberduck.core.ftp.FTPFileType;
import ch.cyberduck.core.ftp.FTPProtocol;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.sftp.SFTPProtocol;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConnectionDialogTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final FTPProtocol ftp = new FTPProtocol() {
        @Override
        public boolean isEnabled() {
            return true;
        }
    };
    private final SFTPProtocol sftp = new SFTPProtocol() {
        @Override
        public boolean isEnabled() {
            return true;
        }
    };

    @Before
    public void setup() throws Exception {
        FxToolkit.init();
        PreferencesFactory.set(new LinuxApplicationPreferences(folder.newFolder().toPath().resolve("cyberduck.properties")));
        ProtocolFactory.get().register(ftp, sftp);
    }

    private static <T> T onFx(final Callable<T> callable) throws Exception {
        final FutureTask<T> task = new FutureTask<>(callable);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testOptionsFollowTheProtocol() throws Exception {
        onFx(() -> {
            final ConnectionDialog dialog = new ConnectionDialog(null, ProtocolFactory.get());
            dialog.getProtocolBox().setValue(ftp);
            assertTrue(dialog.getAnonymousBox().isVisible());
            assertTrue(dialog.getMore().isVisible());
            assertTrue(dialog.getTransferModeBox().isVisible());
            assertTrue(dialog.getConnectModeBox().isVisible());
            dialog.getProtocolBox().setValue(sftp);
            assertFalse(dialog.getAnonymousBox().isVisible());
            assertFalse(dialog.getAnonymousBox().isManaged());
            // The character set can be set but not the modes of FTP
            assertFalse(dialog.getTransferModeBox().isVisible());
            return null;
        });
    }

    @Test
    public void testNoteExplainsWhyUsernameIsOffForCloudLogin() throws Exception {
        final FTPProtocol drive = new FTPProtocol() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public String getName() {
                return "Google Drive";
            }

            @Override
            public boolean isUsernameConfigurable() {
                return false;
            }

            @Override
            public String getOAuthAuthorizationUrl() {
                return "https://accounts.example.net/auth";
            }
        };
        onFx(() -> {
            final ConnectionDialog dialog = new ConnectionDialog(null, ProtocolFactory.get());
            dialog.getProtocolBox().setValue(sftp);
            assertFalse(dialog.getNote().isVisible());
            dialog.getProtocolBox().setValue(drive);
            assertTrue(dialog.getNote().isVisible());
            assertTrue(dialog.getNote().getText().contains("Google Drive"));
            assertTrue(dialog.getUsernameField().isDisabled());
            dialog.getProtocolBox().setValue(sftp);
            assertFalse(dialog.getNote().isVisible());
            assertFalse(dialog.getNote().isManaged());
            return null;
        });
    }

    @Test
    public void testAnonymousLoginTakesTheNameAndPassword() throws Exception {
        onFx(() -> {
            final ConnectionDialog dialog = new ConnectionDialog(null, ProtocolFactory.get());
            dialog.getProtocolBox().setValue(ftp);
            dialog.getServerField().setText("ftp.example.net");
            dialog.getUsernameField().setText("alice");
            dialog.getAnonymousBox().setSelected(true);
            assertTrue(dialog.getUsernameField().isDisabled());
            assertTrue(dialog.getPasswordField().isDisabled());
            assertEquals("", dialog.getUsernameField().getText());
            dialog.getAnonymousBox().setSelected(false);
            assertFalse(dialog.getUsernameField().isDisabled());
            return null;
        });
    }

    @Test
    public void testEditShowsWhatTheBookmarkHas() throws Exception {
        final Host bookmark = new Host(ftp, "ftp.example.net");
        HostBuilder.options(bookmark, true, "ISO-8859-1", FTPConnectMode.active, "auto");
        onFx(() -> {
            final ConnectionDialog dialog = new ConnectionDialog(null, ProtocolFactory.get(), true, bookmark);
            assertTrue(dialog.getAnonymousBox().isSelected());
            assertEquals("ISO-8859-1", dialog.getEncodingBox().getValue());
            assertEquals(FTPConnectMode.active, dialog.getConnectModeBox().getValue());
            assertEquals("auto", dialog.getTransferModeBox().getValue().getValue());
            return null;
        });
    }

    @Test
    public void testDefaultsShowAsDefault() throws Exception {
        final Host bookmark = new Host(ftp, "ftp.example.net");
        onFx(() -> {
            final ConnectionDialog dialog = new ConnectionDialog(null, ProtocolFactory.get(), true, bookmark);
            assertEquals("", dialog.getEncodingBox().getValue());
            assertEquals(FTPConnectMode.unknown, dialog.getConnectModeBox().getValue());
            assertEquals("", dialog.getTransferModeBox().getValue().getValue());
            assertNull(bookmark.getProperty(FTPFileType.MODE));
            return null;
        });
    }

    @Test
    public void testPrivateKeyOnlyForProtocolsWithKeys() throws Exception {
        onFx(() -> {
            final ConnectionDialog dialog = new ConnectionDialog(null, ProtocolFactory.get());
            dialog.getProtocolBox().setValue(sftp);
            assertTrue(dialog.getPrivateKeyField().isVisible());
            assertTrue(dialog.getChooseKeyButton().isVisible());
            dialog.getProtocolBox().setValue(ftp);
            assertFalse(dialog.getPrivateKeyField().isVisible());
            assertFalse(dialog.getPrivateKeyField().isManaged());
            return null;
        });
    }

    @Test
    public void testPrivateKeyIsKeptInTheBookmark() throws Exception {
        final Host bookmark = new Host(sftp, "example.net");
        HostBuilder.identity(bookmark, "/home/user/.ssh/id_rsa");
        onFx(() -> {
            final ConnectionDialog dialog = new ConnectionDialog(null, ProtocolFactory.get(), true, bookmark);
            assertEquals("/home/user/.ssh/id_rsa", dialog.getPrivateKeyField().getText());
            dialog.getPrivateKeyField().setText("/home/user/.ssh/other");
            dialog.getConnectButton().fire();
            return null;
        });
    }

    @Test
    public void testSavePasswordOnlyWithAPassword() throws Exception {
        onFx(() -> {
            final ConnectionDialog dialog = new ConnectionDialog(null, ProtocolFactory.get());
            dialog.getProtocolBox().setValue(sftp);
            dialog.getServerField().setText("example.net");
            dialog.getUsernameField().setText("alice");
            // Nothing to save without a password
            assertTrue(dialog.getSavePasswordBox().isDisabled());
            dialog.getSavePasswordBox().setSelected(true);
            dialog.getConnectButton().fire();
            assertFalse(dialog.getResult().getCredentials().isSaved());
            return null;
        });
        onFx(() -> {
            final ConnectionDialog dialog = new ConnectionDialog(null, ProtocolFactory.get());
            dialog.getProtocolBox().setValue(sftp);
            dialog.getServerField().setText("example.net");
            dialog.getUsernameField().setText("alice");
            dialog.getPasswordField().setText("secret");
            assertFalse(dialog.getSavePasswordBox().isDisabled());
            assertFalse(dialog.getSavePasswordBox().isSelected());
            dialog.getConnectButton().fire();
            assertFalse(dialog.getResult().getCredentials().isSaved());
            dialog.getSavePasswordBox().setSelected(true);
            dialog.getConnectButton().fire();
            assertTrue(dialog.getResult().getCredentials().isSaved());
            return null;
        });
    }
}
