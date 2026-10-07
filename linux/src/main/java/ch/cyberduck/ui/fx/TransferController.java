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
import ch.cyberduck.core.ProgressListener;
import ch.cyberduck.core.SessionPoolFactory;
import ch.cyberduck.core.TransferCollection;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.pool.SessionPool;
import ch.cyberduck.core.threading.TransferBackgroundAction;
import ch.cyberduck.core.threading.TransferCollectionBackgroundAction;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.TransferCallback;
import ch.cyberduck.core.transfer.TransferListener;
import ch.cyberduck.core.transfer.TransferOptions;
import ch.cyberduck.core.transfer.TransferProgress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs transfers on their own connections and keeps them in the list of transfers. One for the whole application.
 */
public class TransferController extends FxController implements TransferListener {
    private static final Logger log = LogManager.getLogger(TransferController.class);

    private static TransferController instance;

    public static synchronized TransferController get() {
        if(null == instance) {
            instance = new TransferController();
        }
        return instance;
    }

    private final TransferCollection collection = TransferCollection.defaultCollection();

    private final AtomicInteger started = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger progress = new AtomicInteger();

    /**
     * Run the transfer in the background and add it to the list of transfers
     *
     * @param transfer Transfer to start
     * @param options  Resume or reload
     * @param listener Receives status messages
     * @param callback Called after the transfer has completed successfully
     */
    public void start(final Transfer transfer, final TransferOptions options, final ProgressListener listener, final TransferCallback callback) {
        final Host source = transfer.getSource();
        final Host destination = transfer.getDestination();
        final TransferBackgroundAction action = new TransferCollectionBackgroundAction(this,
            null == source ? SessionPool.DISCONNECTED : SessionPoolFactory.create(this, source, listener),
            null == destination ? SessionPool.DISCONNECTED : SessionPoolFactory.create(this, destination, listener),
            this, listener, transfer, options) {
            @Override
            public void finish() {
                super.finish();
                if(transfer.isComplete()) {
                    completed.incrementAndGet();
                    callback.complete(transfer);
                }
            }

            @Override
            public void cleanup(final Boolean result, final BackgroundException failure) {
                super.cleanup(result, failure);
                if(failure != null) {
                    log.warn("Transfer {} failed. {}", transfer, failure.getMessage());
                }
            }
        };
        if(!collection.contains(transfer)) {
            collection.add(transfer);
        }
        this.background(action);
    }

    /**
     * @return Number of transfers that finished without failure since the application started
     */
    public int getCompleted() {
        return completed.get();
    }

    /**
     * @return Number of progress notifications received since the application started
     */
    public int getProgressEvents() {
        return progress.get();
    }

    @Override
    public void transferDidStart(final Transfer transfer) {
        log.debug("Transfer {} did start", transfer);
        started.incrementAndGet();
    }

    @Override
    public void transferDidStop(final Transfer transfer) {
        log.debug("Transfer {} did stop", transfer);
    }

    @Override
    public void transferDidProgress(final Transfer transfer, final TransferProgress status) {
        progress.incrementAndGet();
    }
}
