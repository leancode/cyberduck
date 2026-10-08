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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Makes sure that one application runs per user. A link that is clicked in a web browser, such as the answer to a
 * login, starts the application again. That second start passes the link to the first one and ends.
 */
final class SingleInstance implements AutoCloseable {
    private static final Logger log = LogManager.getLogger(SingleInstance.class);

    /**
     * Sent instead of a link to bring the window to the front
     */
    static final String ACTIVATE = "activate";

    private static final String ACKNOWLEDGE = "ok";

    /**
     * @return The socket in the folder of the application
     */
    static Path socket() {
        return Paths.get(System.getProperty("user.home"), ".duck", "cyberduck.sock");
    }

    /**
     * Pass the links to the running application
     *
     * @param socket Where the running application listens
     * @param lines  Links to open. Brings the window to the front if none.
     * @return False if no application answered
     */
    static boolean forward(final Path socket, final List<String> lines) {
        if(!Files.exists(socket)) {
            return false;
        }
        try(SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socket));
            final StringBuilder message = new StringBuilder();
            for(String line : lines.isEmpty() ? List.of(ACTIVATE) : lines) {
                message.append(line.replaceAll("[\\r\\n]", "")).append('\n');
            }
            channel.write(ByteBuffer.wrap(message.toString().getBytes(StandardCharsets.UTF_8)));
            channel.shutdownOutput();
            // An application that does not answer is stuck. Do not wait for it.
            final BufferedReader reader = new BufferedReader(new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));
            return CompletableFuture.supplyAsync(() -> read(reader)).get(5, TimeUnit.SECONDS);
        }
        catch(IOException e) {
            log.debug("No application running at {}. {}", socket, e.getMessage());
            return false;
        }
        catch(Exception e) {
            log.warn("Application at {} does not answer. {}", socket, e.getMessage());
            return false;
        }
    }

    private static boolean read(final BufferedReader reader) {
        try {
            return ACKNOWLEDGE.equals(reader.readLine());
        }
        catch(IOException e) {
            return false;
        }
    }

    /**
     * Receive the links of later starts
     *
     * @param socket  Where to listen
     * @param handler Called for every link, from a thread of its own. Returns when the link has been handled.
     * @return Null if an application already listens or the socket cannot be created
     */
    static SingleInstance listen(final Path socket, final Consumer<String> handler) {
        try {
            Files.createDirectories(socket.getParent());
            if(Files.exists(socket)) {
                try(SocketChannel probe = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                    probe.connect(UnixDomainSocketAddress.of(socket));
                    log.info("Application already running at {}", socket);
                    return null;
                }
                catch(IOException e) {
                    // Left behind by an application that was killed
                    Files.deleteIfExists(socket);
                }
            }
            final ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(UnixDomainSocketAddress.of(socket));
            Files.setPosixFilePermissions(socket, PosixFilePermissions.fromString("rw-------"));
            final SingleInstance instance = new SingleInstance(socket, server, handler);
            instance.start();
            return instance;
        }
        catch(IOException | UnsupportedOperationException | IllegalArgumentException e) {
            log.warn("Failure listening at {}. {}", socket, e.getMessage());
            return null;
        }
    }

    private final Path socket;
    private final ServerSocketChannel server;
    private final Consumer<String> handler;

    private SingleInstance(final Path socket, final ServerSocketChannel server, final Consumer<String> handler) {
        this.socket = socket;
        this.server = server;
        this.handler = handler;
    }

    private void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "single-instance-cleanup"));
        final Thread thread = new Thread(() -> {
            while(server.isOpen()) {
                try {
                    final SocketChannel client = server.accept();
                    final Thread worker = new Thread(() -> this.serve(client), "single-instance-client");
                    worker.setDaemon(true);
                    worker.start();
                }
                catch(IOException e) {
                    if(server.isOpen()) {
                        log.warn("Failure accepting. {}", e.getMessage());
                    }
                }
            }
        }, "single-instance");
        thread.setDaemon(true);
        thread.start();
    }

    private void serve(final SocketChannel client) {
        try(SocketChannel channel = client) {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));
            String line;
            while((line = reader.readLine()) != null) {
                log.debug("Received {}", line);
                handler.accept(line);
            }
            channel.write(ByteBuffer.wrap((ACKNOWLEDGE + "\n").getBytes(StandardCharsets.UTF_8)));
        }
        catch(IOException e) {
            log.warn("Failure reading from client. {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            server.close();
            Files.deleteIfExists(socket);
        }
        catch(IOException e) {
            log.warn("Failure closing {}. {}", socket, e.getMessage());
        }
    }
}
