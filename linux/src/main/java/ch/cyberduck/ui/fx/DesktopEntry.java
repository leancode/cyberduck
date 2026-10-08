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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An application that is described by a desktop entry file of the freedesktop.org specification
 */
public final class DesktopEntry {

    private final String id;
    private final String name;
    private final String exec;
    private final List<String> mimeTypes;
    private final List<String> categories;
    private final boolean hidden;

    DesktopEntry(final String id, final String name, final String exec, final List<String> mimeTypes, final List<String> categories, final boolean hidden) {
        this.id = id;
        this.name = name;
        this.exec = exec;
        this.mimeTypes = mimeTypes;
        this.categories = categories;
        this.hidden = hidden;
    }

    /**
     * @return Null when the file is not an application that can be shown or cannot be read
     */
    public static DesktopEntry parse(final Path file) {
        final List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        }
        catch(IOException | RuntimeException e) {
            return null;
        }
        return parse(file.getFileName().toString(), lines);
    }

    static DesktopEntry parse(final String id, final List<String> lines) {
        boolean main = false;
        String name = null;
        String exec = null;
        String type = null;
        boolean hidden = false;
        List<String> mimes = Collections.emptyList();
        List<String> categories = Collections.emptyList();
        for(String line : lines) {
            final String trimmed = line.trim();
            if(trimmed.startsWith("[")) {
                // Only the first group describes the application, the others are actions
                main = trimmed.equals("[Desktop Entry]");
                continue;
            }
            if(!main || trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            final int equals = trimmed.indexOf('=');
            if(equals < 0) {
                continue;
            }
            final String key = trimmed.substring(0, equals).trim();
            final String value = trimmed.substring(equals + 1).trim();
            switch(key) {
                case "Name":
                    name = value;
                    break;
                case "Exec":
                    exec = value;
                    break;
                case "Type":
                    type = value;
                    break;
                case "NoDisplay":
                case "Hidden":
                    hidden |= "true".equalsIgnoreCase(value);
                    break;
                case "MimeType":
                    mimes = split(value);
                    break;
                case "Categories":
                    categories = split(value);
                    break;
                default:
                    break;
            }
        }
        if(!"Application".equals(type) || null == name || null == exec) {
            return null;
        }
        return new DesktopEntry(id, name, exec, mimes, categories, hidden);
    }

    private static List<String> split(final String value) {
        final List<String> list = new ArrayList<>();
        for(String item : value.split(";")) {
            if(!item.isEmpty()) {
                list.add(item);
            }
        }
        return list;
    }

    /**
     * @return File name of the entry, such as org.gnome.gedit.desktop
     */
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isHidden() {
        return hidden;
    }

    public List<String> getMimeTypes() {
        return mimeTypes;
    }

    public List<String> getCategories() {
        return categories;
    }

    /**
     * @return The command line without the placeholders for the files and the other values that the launcher fills in,
     * for example {@code code --new-window} for {@code code --new-window %F}
     */
    public String getCommand() {
        final StringBuilder command = new StringBuilder();
        for(int i = 0; i < exec.length(); i++) {
            final char c = exec.charAt(i);
            if(c == '%' && i + 1 < exec.length()) {
                final char code = exec.charAt(++i);
                if(code == '%') {
                    command.append('%');
                }
                // The other codes name the files, the icon, the name or the location of the entry
                continue;
            }
            command.append(c);
        }
        return String.join(" ", Arrays.asList(command.toString().trim().split("\\s+")));
    }
}
