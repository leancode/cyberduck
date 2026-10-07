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
import ch.cyberduck.core.Local;
import ch.cyberduck.core.LocalFactory;
import ch.cyberduck.core.LocaleFactory;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.ConnectionCanceledException;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.sftp.SSHFingerprintGenerator;
import ch.cyberduck.core.sftp.openssh.OpenSSHHostKeyVerifier;

import java.security.PublicKey;
import java.text.MessageFormat;

import net.schmizz.sshj.common.KeyType;

/**
 * Verify the SSH host key against the known hosts file and ask whether to trust unknown or changed keys
 */
public class FxHostKeyCallback extends OpenSSHHostKeyVerifier {

    private final DialogService dialogs;

    /**
     * Created by the core with the controller of the window
     */
    public FxHostKeyCallback(final FxController controller) {
        this(LocalFactory.get(PreferencesFactory.get().getProperty("ssh.knownhosts")).setBookmark(
            PreferencesFactory.get().getProperty("ssh.knownhosts.bookmark")), new FxDialogService(controller));
    }

    FxHostKeyCallback(final Local knownHosts, final DialogService dialogs) {
        super(knownHosts);
        this.dialogs = dialogs;
    }

    @Override
    protected boolean isUnknownKeyAccepted(final Host host, final PublicKey key) throws BackgroundException {
        return this.accepted(host, key, LocaleFactory.localizedString("Unknown fingerprint", "Sftp"));
    }

    @Override
    protected boolean isChangedKeyAccepted(final Host host, final PublicKey key) throws BackgroundException {
        return this.accepted(host, key, LocaleFactory.localizedString("Changed fingerprint", "Sftp"));
    }

    private boolean accepted(final Host host, final PublicKey key, final String title) throws BackgroundException {
        final String message = String.format("%s %s?",
            MessageFormat.format(LocaleFactory.localizedString("The fingerprint for the {1} key sent by the server is {0}.", "Sftp"),
                new SSHFingerprintGenerator().fingerprint(key), KeyType.fromKey(key).toString()),
            LocaleFactory.localizedString("Continue", "Credentials"));
        if(!dialogs.confirm(String.format("%s %s", title, host.getHostname()), message,
            LocaleFactory.localizedString("Allow", "Sftp"), LocaleFactory.localizedString("Deny", "Sftp"), false).accepted()) {
            throw new ConnectionCanceledException();
        }
        this.allow(host, key, true);
        return true;
    }
}
