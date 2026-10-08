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
     * Arrow down onto a tray, for downloading. The shape is from img/toolbar/download.pdf of the macOS application.
     */
    public static Node download() {
        return shape("M 3.7 10.4 L 2.8 11.4 L 7.6 15.9 L 12.6 11.5 L 11.8 10.5 L 8.3 13.5 L 8.3 0 L 6.9 0 L 6.9 13.5 Z M 10.4 6.7 L 10.4 8 L 14.1 8 L 14.1 19.3 L 1.3 19.3 L 1.3 8 L 5.1 8 L 5.1 6.7 L 0 6.7 L 0 20.7 L 15.4 20.7 L 15.4 6.7 Z", 16, 21);
    }

    /**
     * Arrow up from a tray, for uploading. The shape is from img/toolbar/upload.pdf of the macOS application.
     */
    public static Node upload() {
        return shape("M 6.7 2.3 L 6.7 14 L 8 14 L 8 2.3 L 11.3 5.2 L 12.2 4.2 L 7.3 0 L 2.7 4.3 L 3.5 5.3 Z M 10 7.3 L 10 8.6 L 13.5 8.6 L 13.5 19.5 L 1.3 19.5 L 1.3 8.6 L 4.9 8.6 L 4.9 7.3 L 0 7.3 L 0 20.8 L 14.8 20.8 L 14.8 7.3 Z", 15, 21);
    }

    /**
     * Circular arrow, for refreshing. The shape is from img/toolbar/reload.pdf of the macOS application.
     */
    public static Node reload() {
        return shape("M 16.2 12 C 16.2 16.2 12.9 19.5 8.7 19.5 C 4.6 19.5 1.2 16.2 1.2 12 C 1.2 7.9 4.6 4.5 8.7 4.5 L 8.7 8.2 L 15.6 4 L 8.7 0 L 8.7 3.3 C 3.9 3.3 0 7.2 0 12 C 0 16.8 3.9 20.7 8.7 20.7 C 13.5 20.7 17.5 16.8 17.5 12 Z", 18, 21);
    }

    /**
     * Folder with a plus, for a new folder. The shape is from img/toolbar/newfolder.pdf of the macOS application.
     */
    public static Node newFolder() {
        return shape("M 1.7 1.2 L 7.7 1.2 L 9.1 3.2 C 9.2 3.6 9.6 3.9 10.2 3.9 L 18.7 3.9 C 19 3.9 19.2 4.2 19.2 4.4 L 19.2 6.3 L 1.2 6.3 L 1.2 1.7 C 1.2 1.4 1.4 1.2 1.7 1.2 M 1.2 17.6 L 1.2 7.5 L 19.2 7.5 L 19.2 8.7 L 20.5 8.7 L 20.5 4.4 C 20.5 3.5 19.7 2.7 18.7 2.7 L 10.2 2.7 L 8.6 0.5 C 8.6 0.4 8.2 0 7.7 0 L 1.7 0 C 0.8 0 0 0.8 0 1.7 L 0 17.6 C 0 18.5 0.8 19.3 1.7 19.3 L 17.5 19.3 L 17.5 18 L 1.7 18 C 1.4 18 1.2 17.8 1.2 17.6 M 29.5 13.9 L 24 13.9 L 24 8.4 L 22.7 8.4 L 22.7 13.9 L 17.2 13.9 L 17.2 15.1 L 22.7 15.1 L 22.7 20.6 L 24 20.6 L 24 15.1 L 29.5 15.1 Z", 30, 21);
    }

    /**
     * Bin, for deleting. The shape is from img/toolbar/trash.pdf of the macOS application.
     */
    public static Node trash() {
        return shape("M 13.1 18.3 C 13.1 18.5 13 18.6 12.8 18.6 L 3.8 18.6 C 3.6 18.6 3.5 18.5 3.5 18.3 L 2.5 3.9 L 14.1 3.9 Z M 6 1.3 L 10.5 1.2 L 10.5 2.6 L 6.1 2.6 Z M 16.6 2.6 L 11.8 2.6 L 11.8 1.2 C 11.8 0.5 11.2 0 10.6 0 L 6 0 C 5.4 0 4.8 0.5 4.8 1.2 L 4.8 2.6 L 0 2.6 L 0 3.9 L 1.3 3.9 L 2.2 18.3 C 2.2 19.2 2.9 19.9 3.8 19.9 L 12.8 19.9 C 13.7 19.9 14.4 19.2 14.4 18.4 L 15.4 3.9 L 16.6 3.9 Z M 8.9 6 L 7.6 6 L 7.6 16.6 L 8.9 16.6 Z M 11.8 6 L 10.5 6 L 10.5 16.6 L 11.8 16.6 Z M 6.1 6 L 4.8 6 L 4.8 16.6 L 6.1 16.6 Z", 17, 20);
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
