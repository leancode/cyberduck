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

import ch.cyberduck.core.Cache;
import ch.cyberduck.core.DescriptiveUrl;
import ch.cyberduck.core.DescriptiveUrlBag;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathAttributes;
import ch.cyberduck.core.Permission;
import ch.cyberduck.core.Session;
import ch.cyberduck.core.UrlProvider;
import ch.cyberduck.core.UserDateFormatterFactory;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.formatter.SizeFormatterFactory;
import ch.cyberduck.core.pool.SessionPool;
import ch.cyberduck.core.threading.WorkerBackgroundAction;
import ch.cyberduck.core.worker.AttributesWorker;
import ch.cyberduck.core.worker.Worker;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.MessageFormat;
import java.util.EnumSet;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

/**
 * The properties of a file or folder: size, modification date, permissions, owner and URL. Shows what the listing has
 * and then reads the attributes again from the server, because a listing may leave out some of them.
 */
public final class InfoController {
    private static final Logger log = LogManager.getLogger(InfoController.class);

    private final BrowserController controller;
    private final SessionPool pool;
    private final Cache<Path> cache;
    private final Path file;

    private Stage stage;
    private long bytes = -1;

    private final Label name = new Label();
    private final Label kind = new Label();
    private final Label where = new Label();
    private final Label size = new Label();
    private final Label modified = new Label();
    private final Label permissions = new Label();
    private final Label owner = new Label();
    private final Label group = new Label();
    private final TextField url = new TextField();

    InfoController(final BrowserController controller, final SessionPool pool, final Cache<Path> cache, final Path file) {
        this.controller = controller;
        this.pool = pool;
        this.cache = cache;
        this.file = file;
    }

    /**
     * Show the window and start reading the attributes and the URL. Call on the JavaFX application thread.
     */
    public void show() {
        stage = new Stage();
        stage.setTitle(MessageFormat.format(Messages.get("{0} Info"), file.getName()));
        stage.setScene(new Scene(this.build(), 480, 320));
        this.update(file.attributes());
        stage.show();
        controller.background(new WorkerBackgroundAction<>(controller, pool, new AttributesWorker(cache, file) {
            @Override
            public void cleanup(final PathAttributes result, final BackgroundException failure) {
                super.cleanup(result, failure);
                if(null == failure && result != null && result != PathAttributes.EMPTY) {
                    update(result);
                }
                else {
                    log.warn("Failure reading attributes of {}. {}", file, null == failure ? "" : failure.getMessage());
                }
            }
        }));
        controller.background(new WorkerBackgroundAction<>(controller, pool, new UrlWorker(file) {
            @Override
            public void cleanup(final DescriptiveUrlBag result, final BackgroundException failure) {
                super.cleanup(result, failure);
                if(result != null && !result.isEmpty()) {
                    final DescriptiveUrl preferred = result.find(DescriptiveUrl.Type.provider);
                    url.setText((preferred != DescriptiveUrl.EMPTY ? preferred : result.iterator().next()).getUrl());
                }
            }
        }));
    }

    private GridPane build() {
        final GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));
        url.setEditable(false);
        GridPane.setHgrow(url, Priority.ALWAYS);
        int row = 0;
        grid.addRow(row++, new Label(Messages.get("Name")), name);
        grid.addRow(row++, new Label(Messages.get("Kind")), kind);
        grid.addRow(row++, new Label(Messages.get("Where")), where);
        grid.addRow(row++, new Label(Messages.get("Size")), size);
        grid.addRow(row++, new Label(Messages.get("Modified")), modified);
        grid.addRow(row++, new Label(Messages.get("Permissions")), permissions);
        grid.addRow(row++, new Label(Messages.get("Owner")), owner);
        grid.addRow(row++, new Label(Messages.get("Group")), group);
        grid.addRow(row, new Label(Messages.get("URL")), url);
        return grid;
    }

    /**
     * Show the attributes. Call on the JavaFX application thread.
     */
    void update(final PathAttributes attributes) {
        name.setText(file.getName());
        kind.setText(Messages.get(file.isDirectory() ? "Folder" : "File"));
        where.setText(file.getParent().getAbsolute());
        bytes = file.isDirectory() ? -1 : attributes.getSize();
        if(bytes < 0) {
            size.setText(StringUtils.EMPTY);
        }
        else {
            size.setText(String.format("%s (%,d bytes)", SizeFormatterFactory.get().format(bytes), bytes));
        }
        modified.setText(attributes.getModificationDate() <= 0 ? StringUtils.EMPTY
            : UserDateFormatterFactory.get().getLongFormat(attributes.getModificationDate()));
        final Permission permission = attributes.getPermission();
        permissions.setText(null == permission || Permission.EMPTY.equals(permission) ? StringUtils.EMPTY
            : String.format("%s (%s)", permission.getSymbol(), permission.getMode()));
        owner.setText(StringUtils.defaultString(attributes.getOwner()));
        group.setText(StringUtils.defaultString(attributes.getGroup()));
    }

    Stage getStage() {
        return stage;
    }

    /**
     * @return Size in bytes shown or -1 for folders and when unknown
     */
    long getBytes() {
        return bytes;
    }

    Label getSize() {
        return size;
    }

    Label getPermissions() {
        return permissions;
    }

    TextField getUrl() {
        return url;
    }

    /**
     * The URL needs the session, because the address of a file depends on the protocol and the connection
     */
    private static class UrlWorker extends Worker<DescriptiveUrlBag> {
        private final Path file;

        UrlWorker(final Path file) {
            this.file = file;
        }

        @Override
        public DescriptiveUrlBag run(final Session<?> session) throws BackgroundException {
            final UrlProvider provider = session.getFeature(UrlProvider.class);
            if(null == provider) {
                return DescriptiveUrlBag.empty();
            }
            return provider.toUrl(file, EnumSet.of(DescriptiveUrl.Type.provider, DescriptiveUrl.Type.http, DescriptiveUrl.Type.origin));
        }

        @Override
        public DescriptiveUrlBag initialize() {
            return DescriptiveUrlBag.empty();
        }

        @Override
        public String getActivity() {
            return MessageFormat.format(Messages.get("Reading metadata of {0}"), file.getName());
        }
    }
}
