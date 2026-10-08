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

import ch.cyberduck.core.Local;
import ch.cyberduck.core.local.Application;
import ch.cyberduck.core.local.ApplicationLauncher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts applications with the arguments as they are, without the shell. A file name with spaces stays one argument.
 */
public class LinuxApplicationLauncher implements ApplicationLauncher {
    private static final Logger log = LogManager.getLogger(LinuxApplicationLauncher.class);

    /**
     * Split a command line at the spaces, except inside quotes
     */
    public static List<String> tokenize(final String command) {
        final List<String> tokens = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean started = false;
        for(int i = 0; i < command.length(); i++) {
            final char c = command.charAt(i);
            if(quote != 0) {
                if(c == quote) {
                    quote = 0;
                }
                else if(c == '\\' && quote == '"' && i + 1 < command.length()) {
                    current.append(command.charAt(++i));
                }
                else {
                    current.append(c);
                }
            }
            else if(c == '"' || c == '\'') {
                quote = c;
                started = true;
            }
            else if(Character.isWhitespace(c)) {
                if(started || current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    started = false;
                }
            }
            else if(c == '\\' && i + 1 < command.length()) {
                current.append(command.charAt(++i));
                started = true;
            }
            else {
                current.append(c);
                started = true;
            }
        }
        if(started || current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private boolean start(final List<String> command) {
        try {
            final Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")))
                .start();
            // Collect the process when it ends so that it does not stay a zombie
            final Thread reaper = new Thread(() -> {
                try {
                    process.waitFor();
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "application-reaper");
            reaper.setDaemon(true);
            reaper.start();
            return true;
        }
        catch(IOException e) {
            log.warn("Failure launching {}. {}", command, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean open(final Local file) {
        return this.start(List.of("xdg-open", file.getAbsolute()));
    }

    @Override
    public boolean open(final Local file, final Application application) {
        final List<String> command = tokenize(application.getIdentifier());
        if(command.isEmpty()) {
            return this.open(file);
        }
        command.add(file.getAbsolute());
        return this.start(command);
    }

    /**
     * Start a tool with several files, such as a comparison of two files
     */
    public boolean open(final Application application, final List<String> files) {
        final List<String> command = tokenize(application.getIdentifier());
        if(command.isEmpty()) {
            return false;
        }
        command.addAll(files);
        return this.start(command);
    }

    @Override
    public boolean open(final Application application, final String args) {
        final List<String> command = tokenize(application.getIdentifier());
        command.addAll(tokenize(args));
        return this.start(command);
    }

    @Override
    public void bounce(final Local file) {
        //
    }
}
