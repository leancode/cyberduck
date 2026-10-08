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
        return shape(path, width, height, "-fx-text-base-color");
    }

    private static Node shape(final String path, final int width, final int height, final String color) {
        final Region icon = new Region();
        icon.setStyle("-fx-shape: \"" + path + "\"; -fx-background-color: " + color + ";"
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

    /**
     * A folder
     */
    public static Node folder() {
        return shape("M0 2 L5 2 L7 4 L14 4 L14 12 L0 12 Z", 14, 12, "#e3a82b");
    }

    /**
     * A file, tinted by what the name says it holds: pictures, sound and video, archives, programs and text
     */
    public static Node file(final String name) {
        return shape("M0 0 L8 0 L12 4 L12 14 L0 14 Z", 12, 14, color(name));
    }

    static String color(final String name) {
        final String extension = org.apache.commons.io.FilenameUtils.getExtension(null == name ? "" : name).toLowerCase(java.util.Locale.ROOT);
        switch(extension) {
            case "png": case "jpg": case "jpeg": case "gif": case "svg": case "webp": case "bmp": case "tif": case "tiff": case "ico":
                return "#4caf7d";
            case "mp3": case "ogg": case "flac": case "wav": case "m4a": case "mp4": case "mkv": case "avi": case "mov": case "webm":
                return "#a26bd6";
            case "zip": case "gz": case "tgz": case "bz2": case "xz": case "7z": case "rar": case "tar": case "deb": case "rpm":
                return "#d9793a";
            case "sh": case "py": case "js": case "java": case "c": case "h": case "cpp": case "go": case "rs": case "php": case "rb": case "html": case "css": case "xml": case "json": case "yml": case "yaml":
                return "#4a90d9";
            default:
                return "#9aa3ad";
        }
    }
}
