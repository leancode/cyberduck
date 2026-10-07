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

import ch.cyberduck.core.DefaultHostPasswordStore;
import ch.cyberduck.core.Scheme;
import ch.cyberduck.core.UnsecureHostPasswordStore;
import ch.cyberduck.core.exception.AccessDeniedException;
import ch.cyberduck.core.exception.LocalAccessDeniedException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Stores passwords in the keyring of the desktop (the Secret Service of GNOME Keyring, KWallet and others) by running
 * <code>secret-tool</code> of libsecret. The password is passed on standard input and never on the command line.
 * <p>
 * Without <code>secret-tool</code> on the machine the passwords are kept in the credentials file as before.
 */
public class SecretToolPasswordStore extends DefaultHostPasswordStore {
    private static final Logger log = LogManager.getLogger(SecretToolPasswordStore.class);

    private static final String EXECUTABLE = "secret-tool";
    private static final long TIMEOUT_SECONDS = 60;

    private final String executable;
    private final UnsecureHostPasswordStore fallback;
    private final long timeout;

    public SecretToolPasswordStore() {
        this(EXECUTABLE, new UnsecureHostPasswordStore());
    }

    public SecretToolPasswordStore(final String executable, final UnsecureHostPasswordStore fallback) {
        this(executable, fallback, TIMEOUT_SECONDS);
    }

    /**
     * @param timeout Seconds to wait for the tool. It waits without limit while the keyring is locked and no one unlocks it.
     */
    public SecretToolPasswordStore(final String executable, final UnsecureHostPasswordStore fallback, final long timeout) {
        this.executable = executable;
        this.fallback = fallback;
        this.timeout = timeout;
    }

    /**
     * @return True if the tool is an executable file or found in one of the directories of PATH
     */
    public boolean isInstalled() {
        if(executable.contains("/")) {
            return Files.isExecutable(Paths.get(executable));
        }
        final String path = System.getenv("PATH");
        if(path == null) {
            return false;
        }
        for(String directory : path.split(":")) {
            if(!directory.isEmpty() && Files.isExecutable(Paths.get(directory, executable))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Result of one run of the tool
     */
    private static final class Result {
        private final int exit;
        private final String output;

        private Result(final int exit, final String output) {
            this.exit = exit;
            this.output = output;
        }
    }

    /**
     * @return Null when the tool is not installed
     */
    private Result run(final String stdin, final String... arguments) throws AccessDeniedException {
        final List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(Arrays.asList(arguments));
        final Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
        }
        catch(IOException e) {
            log.warn("Cannot run {}. {}", executable, e.getMessage());
            return null;
        }
        try {
            try(OutputStream out = process.getOutputStream()) {
                if(stdin != null) {
                    out.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            }
            catch(IOException e) {
                log.warn("Failure writing to {}. {}", executable, e.getMessage());
            }
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            // Read in threads so that the timeout applies even when the tool waits for the keyring to be unlocked
            final Thread out = this.drain(process.getInputStream(), output);
            final Thread err = this.drain(process.getErrorStream(), new ByteArrayOutputStream());
            if(!process.waitFor(timeout, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new LocalAccessDeniedException(String.format("Timeout running %s. Is the keyring locked?", executable));
            }
            out.join(TimeUnit.SECONDS.toMillis(5));
            err.join(TimeUnit.SECONDS.toMillis(5));
            return new Result(process.exitValue(), new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LocalAccessDeniedException(e.getMessage(), e);
        }
        finally {
            process.destroy();
        }
    }

    private Thread drain(final InputStream in, final ByteArrayOutputStream out) {
        final Thread thread = new Thread(() -> read(in, out), "secret-tool-output");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void read(final InputStream in, final ByteArrayOutputStream out) {
        try {
            final byte[] buffer = new byte[4096];
            int read;
            while((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        catch(IOException e) {
            log.debug("Failure reading output. {}", e.getMessage());
        }
    }

    private static String[] generic(final String serviceName, final String accountName) {
        return new String[]{"application", "cyberduck", "kind", "generic", "service", serviceName, "account", accountName};
    }

    private static String[] internet(final Scheme scheme, final int port, final String hostname, final String user) {
        return new String[]{"application", "cyberduck", "kind", "internet", "scheme", scheme.name(), "port", String.valueOf(port),
            "host", hostname, "user", user};
    }

    private static String[] concat(final String[] head, final String[] tail) {
        final String[] all = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, all, head.length, tail.length);
        return all;
    }

    private static String label(final String[] attributes) {
        final StringBuilder b = new StringBuilder("Cyberduck");
        for(int i = 0; i < attributes.length; i += 2) {
            if("application".equals(attributes[i]) || "kind".equals(attributes[i]) || "scheme".equals(attributes[i]) || "port".equals(attributes[i])) {
                continue;
            }
            b.append(' ').append(attributes[i + 1]);
        }
        return b.toString();
    }

    private String lookup(final String[] attributes) throws AccessDeniedException {
        final Result result = this.run(null, concat(new String[]{"lookup"}, attributes));
        if(null == result) {
            return null;
        }
        if(result.exit == 0) {
            return result.output.isEmpty() ? null : result.output;
        }
        if(result.exit == 1 && result.output.isEmpty()) {
            // Not found
            return null;
        }
        throw new LocalAccessDeniedException(String.format("Failure %d searching in keyring", result.exit));
    }

    private boolean store(final String[] attributes, final String password) throws AccessDeniedException {
        final Result result = this.run(password, concat(new String[]{"store", String.format("--label=%s", label(attributes))}, attributes));
        if(null == result) {
            return false;
        }
        if(result.exit != 0) {
            throw new LocalAccessDeniedException(String.format("Failure %d saving to keyring", result.exit));
        }
        return true;
    }

    private boolean clear(final String[] attributes) throws AccessDeniedException {
        final Result result = this.run(null, concat(new String[]{"clear"}, attributes));
        if(null == result) {
            return false;
        }
        if(result.exit != 0) {
            // Nothing to remove is reported as a failure by some versions
            log.debug("Exit {} removing from keyring", result.exit);
        }
        return true;
    }

    @Override
    public String getPassword(final String serviceName, final String accountName) throws AccessDeniedException {
        if(!this.isInstalled()) {
            return fallback.getPassword(serviceName, accountName);
        }
        return this.lookup(generic(serviceName, accountName));
    }

    @Override
    public void addPassword(final String serviceName, final String accountName, final String password) throws AccessDeniedException {
        if(!this.store(generic(serviceName, accountName), password)) {
            fallback.addPassword(serviceName, accountName, password);
        }
    }

    @Override
    public String getPassword(final Scheme scheme, final int port, final String hostname, final String user) throws AccessDeniedException {
        if(!this.isInstalled()) {
            return fallback.getPassword(scheme, port, hostname, user);
        }
        return this.lookup(internet(scheme, port, hostname, user));
    }

    @Override
    public void addPassword(final Scheme scheme, final int port, final String hostname, final String user, final String password) throws AccessDeniedException {
        if(!this.store(internet(scheme, port, hostname, user), password)) {
            fallback.addPassword(scheme, port, hostname, user, password);
        }
    }

    @Override
    public void deletePassword(final String serviceName, final String user) throws AccessDeniedException {
        if(!this.clear(generic(serviceName, user))) {
            fallback.deletePassword(serviceName, user);
        }
    }

    @Override
    public void deletePassword(final Scheme scheme, final int port, final String hostname, final String user) throws AccessDeniedException {
        if(!this.clear(internet(scheme, port, hostname, user))) {
            fallback.deletePassword(scheme, port, hostname, user);
        }
    }
}
