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
import ch.cyberduck.core.StaticPermission;
import ch.cyberduck.core.UrlProvider;
import ch.cyberduck.core.UserDateFormatterFactory;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.UnixPermission;
import ch.cyberduck.core.formatter.SizeFormatterFactory;
import ch.cyberduck.core.pool.SessionPool;
import ch.cyberduck.core.threading.WorkerBackgroundAction;
import ch.cyberduck.core.worker.AttributesWorker;
import ch.cyberduck.core.worker.CalculateSizeWorker;
import ch.cyberduck.core.worker.Worker;
import ch.cyberduck.core.worker.WritePermissionWorker;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.EnumSet;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
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
    /**
     * Size of the content of a folder once it has been calculated, otherwise -1
     */
    private long calculated = -1;
    private final Button calculate = new Button(Messages.get("Calculate"));

    private final Label name = new Label();
    private final Label kind = new Label();
    private final Label where = new Label();
    private final Label size = new Label();
    private final Label modified = new Label();
    private final Label permissions = new Label();
    /**
     * Read, write and execute for the owner, the group and the others
     */
    private final CheckBox[][] bits = new CheckBox[3][3];
    private final TextField octal = new TextField();
    private final CheckBox recursive = new CheckBox(Messages.get("Apply changes to enclosed items"));
    private final Button apply = new Button(Messages.get("Apply"));
    private boolean updating;
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
        stage.setScene(new Scene(this.build(), 520, 520));
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

    GridPane build() {
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
        calculate.setVisible(file.isDirectory());
        calculate.setManaged(file.isDirectory());
        calculate.setOnAction(event -> this.calculate());
        final HBox sizes = new HBox(10, size, calculate);
        sizes.setAlignment(Pos.CENTER_LEFT);
        grid.addRow(row++, new Label(Messages.get("Size")), sizes);
        grid.addRow(row++, new Label(Messages.get("Modified")), modified);
        grid.addRow(row++, new Label(Messages.get("Permissions")), permissions);
        grid.add(this.editor(), 1, row++);
        grid.addRow(row++, new Label(Messages.get("Owner")), owner);
        grid.addRow(row++, new Label(Messages.get("Group")), group);
        grid.addRow(row, new Label(Messages.get("URL")), url);
        return grid;
    }

    /**
     * Boxes for what the owner, the group and the others may do and the same as an octal number
     */
    private Node editor() {
        final GridPane boxes = new GridPane();
        boxes.setHgap(14);
        boxes.setVgap(4);
        final String[] columns = {Messages.get("Read"), Messages.get("Write"), Messages.get("Execute")};
        final String[] rows = {Messages.get("Owner"), Messages.get("Group"), Messages.get("Others")};
        for(int c = 0; c < 3; c++) {
            boxes.add(new Label(columns[c]), c + 1, 0);
        }
        for(int r = 0; r < 3; r++) {
            boxes.add(new Label(rows[r]), 0, r + 1);
            for(int c = 0; c < 3; c++) {
                final CheckBox box = new CheckBox();
                bits[r][c] = box;
                box.setOnAction(event -> this.showOctal());
                boxes.add(box, c + 1, r + 1);
            }
        }
        octal.setPrefColumnCount(4);
        octal.setMaxWidth(70);
        octal.textProperty().addListener((observable, previous, text) -> this.showBoxes(text));
        apply.setOnAction(event -> this.apply());
        final HBox line = new HBox(10, new Label(Messages.get("Octal")), octal, apply);
        line.setAlignment(Pos.CENTER_LEFT);
        final VBox editor = new VBox(8, boxes, line, recursive);
        // What can be changed depends on the protocol
        final boolean editable = null != pool && null != pool.getFeature(UnixPermission.class);
        editor.setDisable(!editable);
        recursive.setVisible(file.isDirectory());
        recursive.setManaged(file.isDirectory());
        return editor;
    }

    private void showOctal() {
        if(updating) {
            return;
        }
        updating = true;
        try {
            final StringBuilder text = new StringBuilder();
            for(int r = 0; r < 3; r++) {
                text.append((bits[r][0].isSelected() ? 4 : 0) + (bits[r][1].isSelected() ? 2 : 0) + (bits[r][2].isSelected() ? 1 : 0));
            }
            octal.setText(text.toString());
        }
        finally {
            updating = false;
        }
    }

    private void showBoxes(final String text) {
        if(updating || null == text || !text.matches("[0-7]{3}") || null == bits[0][0]) {
            return;
        }
        updating = true;
        try {
            for(int r = 0; r < 3; r++) {
                final int digit = text.charAt(r) - '0';
                bits[r][0].setSelected((digit & 4) != 0);
                bits[r][1].setSelected((digit & 2) != 0);
                bits[r][2].setSelected((digit & 1) != 0);
            }
        }
        finally {
            updating = false;
        }
    }

    /**
     * @return The permissions as entered or null when the octal number is not valid
     */
    Permission entered() {
        if(!octal.getText().matches("[0-7]{3}")) {
            return null;
        }
        return new StaticPermission(
            Permission.Action.values()[octal.getText().charAt(0) - '0'],
            Permission.Action.values()[octal.getText().charAt(1) - '0'],
            Permission.Action.values()[octal.getText().charAt(2) - '0']);
    }

    /**
     * Change the permissions of the file, and of what it contains if selected
     */
    void apply() {
        final Permission permission = this.entered();
        if(null == permission || null == controller) {
            return;
        }
        final boolean descend = file.isDirectory() && recursive.isSelected();
        apply.setDisable(true);
        controller.background(new WorkerBackgroundAction<>(controller, pool,
            new WritePermissionWorker(Collections.singletonList(file), permission, (directory, value) -> descend, controller) {
                @Override
                public void cleanup(final Boolean result, final BackgroundException failure) {
                    super.cleanup(result, failure);
                    apply.setDisable(false);
                    file.attributes().setPermission(permission);
                    update(file.attributes());
                    controller.reload();
                }
            }));
    }

    /**
     * Show the attributes. Call on the JavaFX application thread.
     */
    void update(final PathAttributes attributes) {
        name.setText(file.getName());
        kind.setText(Messages.get(file.isDirectory() ? "Folder" : "File"));
        where.setText(file.getParent().getAbsolute());
        bytes = file.isDirectory() ? calculated : attributes.getSize();
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
        if(permission != null && !Permission.EMPTY.equals(permission)) {
            octal.setText(permission.getMode());
            this.showBoxes(octal.getText());
        }
        owner.setText(StringUtils.defaultString(attributes.getOwner()));
        group.setText(StringUtils.defaultString(attributes.getGroup()));
    }

    /**
     * Add up the size of everything in the folder, which can take a while on a big folder
     */
    void calculate() {
        calculate.setDisable(true);
        size.setText(Messages.get("Calculating…"));
        controller.background(new WorkerBackgroundAction<>(controller, pool, new CalculateSizeWorker(Collections.singletonList(file), controller) {
            @Override
            public void update(final long running) {
                Platform.runLater(() -> size.setText(String.format("%s …", SizeFormatterFactory.get().format(running))));
            }

            @Override
            public void cleanup(final Long total) {
                super.cleanup(total);
                calculate.setDisable(false);
                if(total != null) {
                    calculated = total;
                }
                InfoController.this.update(file.attributes());
            }
        }));
    }

    Button getCalculate() {
        return calculate;
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

    TextField getOctal() {
        return octal;
    }

    CheckBox getBit(final int row, final int column) {
        return bits[row][column];
    }

    Button getApply() {
        return apply;
    }

    CheckBox getRecursive() {
        return recursive;
    }

    TextField getUrl() {
        return url;
    }

    /**
     * The URL needs the session, because the address of a file depends on the protocol and the connection
     */
    static class UrlWorker extends Worker<DescriptiveUrlBag> {
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
