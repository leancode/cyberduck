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

import ch.cyberduck.core.BookmarkCollection;
import ch.cyberduck.core.TransferCollection;
import ch.cyberduck.core.preferences.PreferencesFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/**
 * Owns the browser windows and the life cycle of the application. Use on the JavaFX application thread.
 */
public final class MainController {
    private static final Logger log = LogManager.getLogger(MainController.class);

    private static final MainController instance = new MainController();

    public static MainController get() {
        return instance;
    }

    private final List<BrowserController> browsers = new ArrayList<>();
    private boolean quitting;

    private MainController() {
        //
    }

    /**
     * Open a browser window
     *
     * @param stage Window to use or null to create a new one
     */
    public BrowserController newBrowser(final Stage stage) {
        final BrowserController browser = new BrowserController(null == stage ? new Stage() : stage);
        browser.getStage().setOnCloseRequest(event -> {
            // Disconnect first
            event.consume();
            browser.close();
        });
        browsers.add(browser);
        browser.show();
        log.debug("Opened browser window {}", browsers.size());
        return browser;
    }

    public List<BrowserController> getBrowsers() {
        return Collections.unmodifiableList(browsers);
    }

    /**
     * A browser window has been closed
     */
    void closed(final BrowserController browser) {
        browsers.remove(browser);
        if(browsers.isEmpty() && !quitting) {
            // Last window
            this.quit();
        }
    }

    /**
     * Disconnect all browsers, save and end the application. Asks first if transfers are still running.
     */
    public void quit() {
        if(quitting) {
            return;
        }
        final TransferController transfers = TransferController.get();
        final int running = transfers.getRunning();
        if(running > 0) {
            final boolean accepted = new FxDialogService(transfers).confirm("Quit",
                String.format("%d transfers are still running. They will be stopped.", running), "Quit", "Cancel", false).accepted();
            if(!accepted) {
                // Keep the application open, with the transfers in view
                transfers.show();
                return;
            }
            transfers.stopAll();
        }
        quitting = true;
        // A login that waits for the web browser would keep the connection from closing
        FxLoginCallback.cancelAll();
        hideDialogs();
        final List<BrowserController> open = new ArrayList<>(browsers);
        if(open.isEmpty()) {
            this.finish();
            return;
        }
        final AtomicInteger pending = new AtomicInteger(open.size());
        for(BrowserController browser : open) {
            browser.unmount(() -> {
                browser.dispose();
                if(pending.decrementAndGet() == 0) {
                    this.finish();
                }
            });
        }
    }

    private void finish() {
        log.info("Quit");
        browsers.clear();
        BookmarkCollection.defaultCollection().save();
        TransferCollection.defaultCollection().save();
        PreferencesFactory.get().save();
        for(Window window : new ArrayList<>(Window.getWindows())) {
            window.hide();
        }
        Platform.exit();
    }

    /**
     * Close open dialogs. Ending the application while a dialog waits in its nested event loop crashes the JVM.
     */
    static void hideDialogs() {
        for(Window window : new ArrayList<>(Window.getWindows())) {
            if(window.isShowing() && window.getScene() != null && window.getScene().getRoot() instanceof javafx.scene.control.DialogPane) {
                window.fireEvent(new WindowEvent(window, WindowEvent.WINDOW_HIDING));
                window.hide();
            }
        }
    }
}
