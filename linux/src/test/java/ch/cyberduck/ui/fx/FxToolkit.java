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

import org.junit.Assume;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

/**
 * Starts the JavaFX toolkit once for tests. Tests are skipped, not failed, without a display so that the build stays
 * green on headless machines. Run with {@code xvfb-run -a mvn test} to include them.
 */
public final class FxToolkit {
    private static boolean started;

    private FxToolkit() {
        //
    }

    public static synchronized void init() {
        Assume.assumeTrue("No display available", System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null);
        if(!started) {
            Platform.setImplicitExit(false);
            Platform.startup(() -> {
                //
            });
            started = true;
        }
    }

    /**
     * Wait until everything queued on the JavaFX application thread so far has run.
     */
    public static void flush() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        if(!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timeout waiting for JavaFX application thread");
        }
    }
}
