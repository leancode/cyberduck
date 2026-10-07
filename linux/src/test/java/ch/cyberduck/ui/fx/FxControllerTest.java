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

import ch.cyberduck.core.threading.DefaultMainAction;
import ch.cyberduck.core.threading.MainAction;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.application.Platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FxControllerTest {

    @Before
    public void toolkit() {
        FxToolkit.init();
    }

    @Test(timeout = 20000L)
    public void testInvokeWaitRunsOnApplicationThreadBeforeReturning() throws Exception {
        final FxController controller = new FxController();
        final AtomicBoolean onFx = new AtomicBoolean();
        final AtomicBoolean finished = new AtomicBoolean();
        final AtomicBoolean returnedAfterFinished = new AtomicBoolean();
        final Thread worker = new Thread(() -> {
            controller.invoke(new DefaultMainAction() {
                @Override
                public void run() {
                    onFx.set(Platform.isFxApplicationThread());
                    finished.set(true);
                }
            }, true);
            returnedAfterFinished.set(finished.get());
        });
        worker.start();
        worker.join();
        assertTrue("Action runs on the JavaFX application thread", onFx.get());
        assertTrue("Invoke with wait returns after the action ran", returnedAfterFinished.get());
    }

    @Test(timeout = 20000L)
    public void testInvokeWithoutWaitIsAsynchronous() throws Exception {
        final FxController controller = new FxController();
        final AtomicInteger counter = new AtomicInteger();
        final Thread worker = new Thread(() -> controller.invoke(new DefaultMainAction() {
            @Override
            public void run() {
                counter.incrementAndGet();
            }
        }));
        worker.start();
        worker.join();
        FxToolkit.flush();
        assertEquals(1, counter.get());
    }

    @Test(timeout = 20000L)
    public void testInvalidActionIsSkipped() throws Exception {
        final FxController controller = new FxController();
        final AtomicBoolean ran = new AtomicBoolean();
        controller.invoke(new MainAction() {
            @Override
            public boolean isValid() {
                return false;
            }

            @Override
            public Object lock() {
                return this;
            }

            @Override
            public void run() {
                ran.set(true);
            }
        }, true);
        FxToolkit.flush();
        assertFalse(ran.get());
    }

    @Test(timeout = 20000L)
    public void testMessageFromBackgroundThreadUpdatesProperty() throws Exception {
        final FxController controller = new FxController();
        final Thread worker = new Thread(() -> {
            controller.message("Listing directory");
            controller.log(ch.cyberduck.core.TranscriptListener.Type.request, "LIST");
        });
        worker.start();
        worker.join();
        FxToolkit.flush();
        assertEquals("Listing directory", controller.messageProperty().get());
        assertEquals("LIST", controller.transcriptProperty().get());
    }
}
