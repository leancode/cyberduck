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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Where files wait that are dragged out of the browser to another application. The files that are dragged have to be
 * given to the other application when the drag starts, but they are still on the server. So they are downloaded to a
 * folder where the other application does not look and moved to the folder with the names it was given when they are
 * complete. A drop before that finds no file, instead of a part of one.
 */
public final class DragStaging {
    private static final Logger log = LogManager.getLogger(DragStaging.class);

    private static Path root;

    private DragStaging() {
        //
    }

    private static synchronized Path root() throws IOException {
        if(null == root) {
            root = Files.createTempDirectory("cyberduck-drag");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> delete(root), "drag-staging-cleanup"));
        }
        return root;
    }

    /**
     * Folder for one drag
     */
    public static final class Batch {
        private final Path work;
        private final Path ready;

        private Batch(final Path work, final Path ready) {
            this.work = work;
            this.ready = ready;
        }

        /**
         * @return Where the download writes
         */
        public Path work(final String name) {
            return work.resolve(name);
        }

        /**
         * @return Where the other application finds the file, once it is complete
         */
        public Path ready(final String name) {
            return ready.resolve(name);
        }

        /**
         * Make the downloaded files available under the names that were given to the other application
         */
        public void publish(final List<String> names) {
            for(String name : names) {
                try {
                    Files.move(work(name), ready(name), StandardCopyOption.ATOMIC_MOVE);
                }
                catch(IOException e) {
                    log.warn("Failure making {} available. {}", name, e.getMessage());
                }
            }
        }
    }

    public static Batch create() throws IOException {
        final Path batch = Files.createTempDirectory(root(), "drag");
        return new Batch(Files.createDirectory(batch.resolve("work")), Files.createDirectory(batch.resolve("ready")));
    }

    static void delete(final Path folder) {
        if(null == folder || !Files.exists(folder)) {
            return;
        }
        try(Stream<Path> walk = Files.walk(folder)) {
            for(Path path : walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.deleteIfExists(path);
            }
        }
        catch(IOException e) {
            log.warn("Failure removing {}. {}", folder, e.getMessage());
        }
    }
}
