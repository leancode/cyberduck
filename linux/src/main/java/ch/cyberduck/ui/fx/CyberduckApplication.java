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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

public class CyberduckApplication extends Application {
    private static final Logger log = LogManager.getLogger(CyberduckApplication.class);

    /**
     * Close all windows including open dialogs, then end the application. Calling {@link Platform#exit()} while a dialog
     * is waiting in its nested event loop crashes the JVM, so the dialogs are hidden first.
     */
    public static void quit() {
        for(Window window : new ArrayList<>(Window.getWindows())) {
            if(window.isShowing()) {
                window.hide();
            }
        }
        Platform.runLater(Platform::exit);
    }

    @Override
    public void init() {
        // Not on the JavaFX application thread
        Bootstrap.loadBookmarks();
    }

    @Override
    public void start(final Stage stage) {
        // The application ends when the last browser window has been closed and everything has been saved
        Platform.setImplicitExit(false);
        final BrowserController browser = MainController.get().newBrowser(stage);
        final List<String> arguments = this.getParameters().getRaw();
        final int exit = arguments.indexOf("--exit-after");
        if(exit >= 0 && exit + 1 < arguments.size()) {
            final double seconds = Double.parseDouble(arguments.get(exit + 1));
            log.info("Exit after {} seconds", seconds);
            final PauseTransition timer = new PauseTransition(Duration.seconds(seconds));
            timer.setOnFinished(event -> quit());
            timer.play();
        }
        final int smoke = arguments.indexOf("--smoke");
        if(smoke >= 0) {
            // Scenario runs on its own thread and drives the window like a user would
            final List<String> scenario = arguments.subList(smoke + 1, arguments.size());
            final Thread thread = new Thread(() -> {
                final int code = Smoke.run(scenario, browser);
                if(code != 0 || !Smoke.endsApplication(scenario)) {
                    System.exit(code);
                }
            }, "smoke");
            thread.setDaemon(true);
            thread.start();
        }
    }
}
