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

import ch.cyberduck.core.local.Application;
import ch.cyberduck.core.local.ApplicationFinder;
import ch.cyberduck.core.preferences.PreferencesFactory;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Finds the applications of the desktop for a file type. An application is described by the command to start it,
 * which is the identifier, and the name for the lists. The editor for an extension that the user chose in the
 * preferences wins over the default application of the desktop for the type of the file.
 */
public class LinuxApplicationFinder implements ApplicationFinder {
    private static final Logger log = LogManager.getLogger(LinuxApplicationFinder.class);

    /**
     * Types that the desktop does not know from the name, mostly files with text in them
     */
    private static final Map<String, String> TYPES = new LinkedHashMap<>();

    static {
        for(String extension : new String[]{"txt", "log", "cfg", "conf", "ini", "properties", "csv", "tsv", "yml", "yaml", "toml",
            "sh", "bash", "py", "rb", "pl", "php", "java", "c", "h", "cpp", "go", "rs", "js", "ts", "sql", "tex", "htaccess"}) {
            TYPES.put(extension, "text/plain");
        }
        TYPES.put("md", "text/markdown");
        TYPES.put("html", "text/html");
        TYPES.put("htm", "text/html");
        TYPES.put("css", "text/css");
        TYPES.put("xml", "application/xml");
        TYPES.put("json", "application/json");
    }

    private final List<Path> directories;
    private final String prefix;

    public LinuxApplicationFinder() {
        this(applicationDirectories(), "linux.editor.");
    }

    /**
     * @param directories Folders with desktop entries
     * @param prefix      Start of the names of the preferences with the editor for an extension
     */
    public LinuxApplicationFinder(final List<Path> directories, final String prefix) {
        this.directories = directories;
        this.prefix = prefix;
    }

    static List<Path> applicationDirectories() {
        final List<Path> directories = new ArrayList<>();
        final String home = System.getenv("XDG_DATA_HOME");
        directories.add(StringUtils.isNotBlank(home) ? Paths.get(home, "applications")
            : Paths.get(LinuxApplicationPreferences.userHome(), ".local", "share", "applications"));
        final String dirs = StringUtils.defaultIfBlank(System.getenv("XDG_DATA_DIRS"), "/usr/local/share:/usr/share");
        for(String dir : dirs.split(":")) {
            if(!dir.isEmpty()) {
                directories.add(Paths.get(dir, "applications"));
            }
        }
        return directories;
    }

    /**
     * @return All applications that are shown to the user, by the file name of the entry
     */
    List<DesktopEntry> entries() {
        final Map<String, DesktopEntry> entries = new LinkedHashMap<>();
        for(Path directory : directories) {
            if(!Files.isDirectory(directory)) {
                continue;
            }
            try(DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.desktop")) {
                for(Path file : stream) {
                    // The first folder wins, as the desktop does it
                    if(!entries.containsKey(file.getFileName().toString())) {
                        final DesktopEntry entry = DesktopEntry.parse(file);
                        if(entry != null && !entry.isHidden()) {
                            entries.put(entry.getId(), entry);
                        }
                    }
                }
            }
            catch(IOException e) {
                log.warn("Failure reading applications from {}. {}", directory, e.getMessage());
            }
        }
        return new ArrayList<>(entries.values());
    }

    /**
     * @return Type of the file by its name or null when it is not known
     */
    static String type(final String filename) {
        final String extension = StringUtils.lowerCase(FilenameUtils.getExtension(filename));
        if(StringUtils.isBlank(extension)) {
            return null;
        }
        final String known = TYPES.get(extension);
        if(known != null) {
            return known;
        }
        try {
            return Files.probeContentType(Paths.get(filename));
        }
        catch(IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isText(final String type) {
        return type != null && (type.startsWith("text/") || type.endsWith("+xml") || type.endsWith("/json") || type.endsWith("/xml"));
    }

    private Application application(final DesktopEntry entry) {
        return new LinuxApplication(entry.getCommand(), entry.getName());
    }

    @Override
    public List<Application> findAll(final String filename) {
        final String type = type(filename);
        final List<Application> found = new ArrayList<>();
        final List<DesktopEntry> entries = this.entries();
        entries.sort(Comparator.comparing(DesktopEntry::getName, String.CASE_INSENSITIVE_ORDER));
        for(DesktopEntry entry : entries) {
            final boolean handles = type != null && (entry.getMimeTypes().contains(type)
                || isText(type) && entry.getMimeTypes().contains("text/plain"));
            final boolean editor = type == null && entry.getCategories().contains("TextEditor");
            if((handles || editor) && this.isInstalled(this.application(entry)) && !found.contains(this.application(entry))) {
                found.add(this.application(entry));
            }
        }
        return found;
    }

    /**
     * The editor chosen for the extension, or else the application that the desktop has for the type of the file
     */
    @Override
    public Application find(final String filename) {
        final String extension = StringUtils.lowerCase(FilenameUtils.getExtension(filename));
        if(StringUtils.isNotBlank(extension)) {
            final String chosen = PreferencesFactory.get().getProperty(prefix + extension);
            if(StringUtils.isNotBlank(chosen)) {
                final Application application = this.getDescription(chosen);
                if(this.isInstalled(application)) {
                    return application;
                }
            }
        }
        final String type = type(filename);
        if(null == type) {
            return Application.notfound;
        }
        final String id = this.query(type);
        if(StringUtils.isNotBlank(id)) {
            for(DesktopEntry entry : this.entries()) {
                if(entry.getId().equals(id)) {
                    final Application application = this.application(entry);
                    if(this.isInstalled(application)) {
                        return application;
                    }
                }
            }
        }
        return Application.notfound;
    }

    /**
     * @return File name of the desktop entry that opens the type or null
     */
    private String query(final String type) {
        try {
            final Process process = new ProcessBuilder("xdg-mime", "query", "default", type).redirectErrorStream(false).start();
            process.getOutputStream().close();
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                final String line = reader.readLine();
                if(!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return null;
                }
                return StringUtils.trimToNull(line);
            }
        }
        catch(IOException e) {
            log.debug("Cannot run xdg-mime. {}", e.getMessage());
            return null;
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public boolean isInstalled(final Application application) {
        if(null == application || StringUtils.isBlank(application.getIdentifier())) {
            return false;
        }
        final List<String> command = LinuxApplicationLauncher.tokenize(application.getIdentifier());
        if(command.isEmpty()) {
            return false;
        }
        final String executable = command.get(0);
        if(executable.contains("/")) {
            return new File(executable).canExecute();
        }
        final String path = System.getenv("PATH");
        if(null == path) {
            return false;
        }
        for(String directory : path.split(":")) {
            if(!directory.isEmpty() && new File(directory, executable).canExecute()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param identifier The command that starts the application
     */
    @Override
    public Application getDescription(final String identifier) {
        if(StringUtils.isBlank(identifier)) {
            return Application.notfound;
        }
        for(DesktopEntry entry : this.entries()) {
            if(entry.getCommand().equals(identifier.trim())) {
                return this.application(entry);
            }
        }
        final List<String> command = LinuxApplicationLauncher.tokenize(identifier);
        final String name = command.isEmpty() ? identifier : new File(command.get(0)).getName();
        return new LinuxApplication(identifier.trim(), name.toLowerCase(Locale.ROOT).equals(name) ? StringUtils.capitalize(name) : name);
    }
}
