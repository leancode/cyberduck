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
import ch.cyberduck.core.Local;
import ch.cyberduck.core.LoginCallback;
import ch.cyberduck.core.LoginOptions;
import ch.cyberduck.core.exception.ConnectionCanceledException;
import ch.cyberduck.core.exception.LoginCanceledException;
import ch.cyberduck.core.preferences.PreferencesFactory;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import com.google.common.util.concurrent.Uninterruptibles;

/**
 * Ask for credentials and confirmations while connecting
 */
public class FxLoginCallback extends FxPasswordCallback implements LoginCallback {

    private final FxController controller;
    private final DialogService dialogs;

    /**
     * Created by the core with the controller of the window
     */
    public FxLoginCallback(final FxController controller) {
        this(controller, new FxDialogService(controller));
    }

    FxLoginCallback(final FxController controller, final DialogService dialogs) {
        super(dialogs);
        this.controller = controller;
        this.dialogs = dialogs;
    }

    @Override
    public Credentials prompt(final Host bookmark, final String username, final String title, final String reason, final LoginOptions options) throws LoginCanceledException {
        final Credentials credentials = dialogs.credentials(bookmark, username, title, reason, options);
        if(null == credentials) {
            throw new LoginCanceledException();
        }
        return credentials;
    }

    @Override
    public Local select(final Local identity) {
        return identity;
    }

    @Override
    public void warn(final Host bookmark, final String title, final String message, final String defaultButton,
                     final String cancelButton, final String preference) throws ConnectionCanceledException {
        final boolean suppressible = StringUtils.isNotBlank(preference);
        if(suppressible && PreferencesFactory.get().getBoolean(preference)) {
            // Asked never to be warned again
            return;
        }
        final DialogService.Confirmation choice = dialogs.confirm(title, message, defaultButton, cancelButton, suppressible);
        if(choice.suppressed()) {
            PreferencesFactory.get().setProperty(preference, true);
        }
        if(!choice.accepted()) {
            throw new LoginCanceledException();
        }
    }

    @Override
    public void await(final CountDownLatch signal, final Host bookmark, final String title, final String message) {
        controller.message(message);
        // Whoever gives up releases the signal without an answer, which ends the login as cancelled
        final Runnable close = dialogs.waiting(title, message, signal::countDown);
        synchronized(WAITING) {
            WAITING.computeIfAbsent(controller, key -> new HashSet<>()).add(signal);
        }
        try {
            Uninterruptibles.awaitUninterruptibly(signal);
        }
        finally {
            synchronized(WAITING) {
                final Set<CountDownLatch> signals = WAITING.get(controller);
                if(signals != null) {
                    signals.remove(signal);
                    if(signals.isEmpty()) {
                        WAITING.remove(controller);
                    }
                }
            }
            close.run();
        }
    }

    /**
     * Logins that wait for an answer from a web browser, by window
     */
    private static final Map<FxController, Set<CountDownLatch>> WAITING = new HashMap<>();

    /**
     * Stop waiting for the web browser. Needed to disconnect or to quit while a login waits.
     *
     * @param controller Window that waits
     */
    static void cancel(final FxController controller) {
        final Set<CountDownLatch> signals;
        synchronized(WAITING) {
            signals = new HashSet<>(WAITING.getOrDefault(controller, Collections.emptySet()));
        }
        signals.forEach(CountDownLatch::countDown);
    }

    /**
     * Stop waiting for the web browser in all windows
     */
    static void cancelAll() {
        final Set<CountDownLatch> signals = new HashSet<>();
        synchronized(WAITING) {
            WAITING.values().forEach(signals::addAll);
        }
        signals.forEach(CountDownLatch::countDown);
    }
}
