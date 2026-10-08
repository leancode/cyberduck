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

import ch.cyberduck.core.AbstractController;
import ch.cyberduck.core.threading.MainAction;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CountDownLatch;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Controller running main actions on the JavaFX application thread. Background actions run on the thread pools of
 * the core and report back through {@link #invoke(MainAction)}.
 */
public class FxController extends AbstractController {
    private static final Logger log = LogManager.getLogger(FxController.class);

    private final StringProperty message = new SimpleStringProperty(this, "message", "");
    private final StringProperty transcript = new SimpleStringProperty(this, "transcript", "");

    /**
     * Run on the JavaFX application thread. Runs directly when already on that thread.
     */
    @Override
    public void invoke(final MainAction runnable, final boolean wait) {
        if(!runnable.isValid()) {
            log.debug("Skip invalid action {}", runnable);
            return;
        }
        if(Platform.isFxApplicationThread()) {
            runnable.run();
            return;
        }
        if(!wait) {
            Platform.runLater(runnable);
            return;
        }
        final CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                runnable.run();
            }
            finally {
                done.countDown();
            }
        });
        try {
            done.await();
        }
        catch(InterruptedException e) {
            log.warn("Interrupted waiting for action {}", runnable);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Last status message. Bind to this from views.
     */
    public ReadOnlyStringProperty messageProperty() {
        return message;
    }

    /**
     * Last line of the protocol transcript.
     */
    public ReadOnlyStringProperty transcriptProperty() {
        return transcript;
    }

    @Override
    public void message(final String message) {
        super.message(message);
        this.later(() -> this.message.set(message));
    }

    private final javafx.collections.ObservableList<String> lines = javafx.collections.FXCollections.observableArrayList();

    /**
     * What was sent to the server and what it answered, oldest first, for the log of the connection
     */
    public javafx.collections.ObservableList<String> getTranscriptLines() {
        return lines;
    }

    @Override
    public void log(final Type request, final String message) {
        super.log(request, message);
        this.later(() -> {
            this.transcript.set(message);
            lines.add(String.format("%s %s", Type.request == request ? ">" : "<", StringUtils.stripEnd(message, "\r\n")));
            if(lines.size() > 5000) {
                lines.remove(0, lines.size() - 5000);
            }
        });
    }

    private void later(final Runnable action) {
        if(Platform.isFxApplicationThread()) {
            action.run();
        }
        else {
            Platform.runLater(action);
        }
    }
}
