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

import ch.cyberduck.core.preferences.PreferencesFactory;

import org.apache.commons.lang3.StringUtils;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.stage.Window;

/**
 * Light or dark windows: as the desktop session has it, or as chosen in the preferences
 */
public final class Theme {

    /**
     * Name of the preference: system, light or dark
     */
    public static final String PROPERTY = "linux.theme";

    /**
     * The standard style derives its colors from the base and the background; the lists have a background of their own
     */
    static final String DARK = "-fx-base: #3c3f41; -fx-background: #2b2b2b; -fx-control-inner-background: #2b2b2b; -fx-control-inner-background-alt: #303234;"
        // Flat, like the light windows: the standard style shades the menu bar, the buttons and the column headers from light to dark
        + " -fx-body-color: -fx-color; -fx-inner-border: -fx-color; -fx-shadow-highlight-color: transparent;";

    /**
     * The hints of the text fields would be darker than their background otherwise
     */
    static final String DARK_SHEET = "data:text/css;charset=utf-8,.text-input%20%7B%20-fx-prompt-text-fill%3A%20%23909090%3B%20%7D";

    private static final String ORIGINAL = "linux.style.original";

    private Theme() {
        //
    }

    /**
     * @return True when the windows are dark
     */
    public static boolean isDark() {
        return isDark(PreferencesFactory.get().getProperty(PROPERTY), Platform.getPreferences().getColorScheme());
    }

    static boolean isDark(final String chosen, final ColorScheme system) {
        if("dark".equals(chosen)) {
            return true;
        }
        if("light".equals(chosen)) {
            return false;
        }
        return ColorScheme.DARK == system;
    }

    /**
     * Style every window that is open and every window that opens, and follow the desktop when it changes. Call on the
     * JavaFX application thread.
     */
    public static void install() {
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while(change.next()) {
                for(Window window : change.getAddedSubList()) {
                    watch(window);
                }
            }
        });
        for(Window window : Window.getWindows()) {
            watch(window);
        }
        Platform.getPreferences().colorSchemeProperty().addListener((observable, previous, scheme) -> refresh());
    }

    private static void watch(final Window window) {
        window.sceneProperty().addListener((observable, previous, scene) -> apply(scene));
        apply(window.getScene());
    }

    /**
     * Style the windows again, for example after the choice in the preferences changed
     */
    public static void refresh() {
        for(Window window : Window.getWindows()) {
            apply(window.getScene());
        }
    }

    static void apply(final Scene scene) {
        if(null == scene || null == scene.getRoot()) {
            return;
        }
        // What the window had before is kept, so that it can come back
        final String original = (String) scene.getRoot().getProperties().computeIfAbsent(ORIGINAL, key -> StringUtils.defaultString(scene.getRoot().getStyle()));
        final boolean dark = isDark();
        scene.getRoot().setStyle(dark ? String.format("%s %s", original, DARK).strip() : original);
        if(dark) {
            if(!scene.getStylesheets().contains(DARK_SHEET)) {
                scene.getStylesheets().add(DARK_SHEET);
            }
        }
        else {
            scene.getStylesheets().remove(DARK_SHEET);
        }
    }
}
