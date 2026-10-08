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

import javafx.scene.Node;
import javafx.scene.layout.Region;

/**
 * Small icons drawn as shapes, so they follow the text color of the theme and need no image files
 */
public final class Icons {

    private Icons() {
        //
    }

    private static Node shape(final String path, final int width, final int height) {
        final Region icon = new Region();
        icon.setStyle("-fx-shape: \"" + path + "\"; -fx-background-color: -fx-text-base-color;"
            + " -fx-min-width: " + width + "; -fx-pref-width: " + width + "; -fx-max-width: " + width + ";"
            + " -fx-min-height: " + height + "; -fx-pref-height: " + height + "; -fx-max-height: " + height + ";");
        icon.setMouseTransparent(true);
        return icon;
    }

    /**
     * Triangle that points to the left, for going back
     */
    public static Node back() {
        return shape("M 9 0 L 0 7 L 9 14 Z", 9, 14);
    }

    /**
     * Triangle that points to the right, for going forward
     */
    public static Node forward() {
        return shape("M 0 0 L 9 7 L 0 14 Z", 9, 14);
    }

    /**
     * Triangle that points up, for going to the parent folder
     */
    public static Node up() {
        return shape("M 0 9 L 7 0 L 14 9 Z", 14, 9);
    }
}
