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

import ch.cyberduck.core.Host;
import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.preferences.Preferences;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.TransferAction;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

/**
 * The preferences window. A change is saved to the preferences file when it is made, there is nothing to confirm.
 */
public final class PreferencesController {

    private static PreferencesController instance;

    /**
     * @return The window of the application
     */
    public static synchronized PreferencesController get() {
        if(null == instance) {
            instance = new PreferencesController(PreferencesFactory.get());
        }
        return instance;
    }

    /**
     * A value of a preference with the text to show for it
     */
    static final class Choice {
        private final String value;
        private final String label;

        Choice(final String value, final String label) {
            this.value = value;
            this.label = label;
        }

        String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Preferences preferences;
    private Stage stage;

    private final TextField downloadFolder = new TextField();
    private final CheckBox showHidden = new CheckBox(Messages.get("Show hidden files"));
    private final ComboBox<Protocol> protocol = new ComboBox<>();
    private final ComboBox<Choice> downloadAction = new ComboBox<>();
    private final ComboBox<Choice> uploadAction = new ComboBox<>();
    private final ComboBox<Choice> transferType = new ComboBox<>();
    private final Spinner<Integer> timeout = new Spinner<>(1, 600, 30);
    private final Spinner<Integer> retries = new Spinner<>(0, 20, 1);
    private final CheckBox proxy = new CheckBox(Messages.get("Use the proxy of the system"));

    PreferencesController(final Preferences preferences) {
        this.preferences = preferences;
    }

    /**
     * Show the window. Call on the JavaFX application thread.
     */
    public void show() {
        if(null == stage) {
            stage = new Stage();
            stage.setTitle(Messages.get("Preferences"));
            stage.setScene(new Scene(this.build(), 560, 380));
        }
        stage.show();
        stage.toFront();
    }

    Stage getStage() {
        return stage;
    }

    private void save(final String property, final String value) {
        if(StringUtils.isEmpty(value)) {
            preferences.deleteProperty(property);
        }
        else {
            preferences.setProperty(property, value);
        }
        preferences.save();
    }

    private void save(final String property, final boolean value) {
        preferences.setProperty(property, value);
        preferences.save();
    }

    private void save(final String property, final int value) {
        preferences.setProperty(property, value);
        preferences.save();
    }

    /**
     * Read the values from the preferences, then start saving changes.
     */
    Parent build() {
        final TabPane tabs = new TabPane(
            new Tab(Messages.get("General"), this.general()),
            new Tab(Messages.get("Transfers"), this.transfers()),
            new Tab(Messages.get("Connection"), this.connection()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return tabs;
    }

    private GridPane grid() {
        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        return grid;
    }

    private Parent general() {
        final GridPane grid = this.grid();
        downloadFolder.setText(preferences.getProperty("queue.download.folder"));
        downloadFolder.setOnAction(event -> this.save("queue.download.folder", downloadFolder.getText().trim()));
        downloadFolder.focusedProperty().addListener((observable, was, focused) -> {
            if(!focused) {
                this.save("queue.download.folder", downloadFolder.getText().trim());
            }
        });
        final Button choose = new Button(Messages.get("Choose…"));
        choose.setOnAction(event -> {
            final DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(Messages.get("Download Folder"));
            final File current = new File(downloadFolder.getText());
            if(current.isDirectory()) {
                chooser.setInitialDirectory(current);
            }
            final File selected = chooser.showDialog(stage);
            if(selected != null) {
                downloadFolder.setText(selected.getAbsolutePath());
                this.save("queue.download.folder", selected.getAbsolutePath());
            }
        });
        HBox.setHgrow(downloadFolder, Priority.ALWAYS);
        grid.addRow(0, new Label(Messages.get("Download Folder")), new HBox(8, downloadFolder, choose));

        showHidden.setSelected(preferences.getBoolean("browser.showHidden"));
        showHidden.setOnAction(event -> this.save("browser.showHidden", showHidden.isSelected()));
        grid.add(showHidden, 1, 1);

        final List<Protocol> protocols = new ArrayList<>(ProtocolFactory.get().find());
        protocol.getItems().setAll(protocols);
        protocol.setCellFactory(list -> new ProtocolListCell());
        protocol.setButtonCell(new ProtocolListCell());
        final String preferred = preferences.getProperty("connection.protocol.default");
        protocol.setValue(StringUtils.isBlank(preferred) ? null : ProtocolFactory.get().forName(preferred));
        protocol.valueProperty().addListener((observable, previous, selected) -> {
            if(selected != null) {
                this.save("connection.protocol.default", selected.getIdentifier());
            }
        });
        grid.addRow(2, new Label(Messages.get("Default Protocol")), protocol);
        GridPane.setHgrow(grid.getChildren().get(1), Priority.ALWAYS);
        return grid;
    }

    private static final class ProtocolListCell extends ListCell<Protocol> {
        @Override
        protected void updateItem(final Protocol item, final boolean empty) {
            super.updateItem(item, empty);
            setText(empty || null == item ? null : item.getDescription());
        }
    }

    private List<Choice> actions(final Transfer.Type type) {
        final List<Choice> choices = new ArrayList<>();
        choices.add(new Choice("ask", Messages.get("Ask me what to do")));
        for(TransferAction action : TransferAction.forTransfer(type)) {
            choices.add(new Choice(action.name(), action.getTitle()));
        }
        return choices;
    }

    private void choose(final ComboBox<Choice> box, final List<Choice> choices, final String property, final String fallback) {
        box.getItems().setAll(choices);
        final String current = preferences.getProperty(property);
        box.setValue(choices.stream().filter(c -> c.getValue().equals(current)).findFirst()
            .orElse(choices.stream().filter(c -> c.getValue().equals(fallback)).findFirst().orElse(null)));
        box.valueProperty().addListener((observable, previous, selected) -> {
            if(selected != null) {
                this.save(property, selected.getValue());
            }
        });
    }

    private Parent transfers() {
        final GridPane grid = this.grid();
        this.choose(downloadAction, this.actions(Transfer.Type.download), "queue.download.action", "ask");
        grid.addRow(0, new Label(Messages.get("When a file to download exists")), downloadAction);
        this.choose(uploadAction, this.actions(Transfer.Type.upload), "queue.upload.action", "ask");
        grid.addRow(1, new Label(Messages.get("When a file to upload exists")), uploadAction);
        final List<Choice> types = new ArrayList<>();
        for(Host.TransferType type : Host.TransferType.values()) {
            if(Host.TransferType.unknown != type) {
                types.add(new Choice(type.name(), type.toString()));
            }
        }
        this.choose(transferType, types, "queue.transfer.type", "concurrent");
        grid.addRow(2, new Label(Messages.get("Transfer Files")), transferType);
        return grid;
    }

    private void commit(final Spinner<Integer> spinner, final String property) {
        spinner.setEditable(true);
        spinner.getValueFactory().setValue(Math.max(
            ((javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory) spinner.getValueFactory()).getMin(),
            Math.min(((javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory) spinner.getValueFactory()).getMax(), preferences.getInteger(property))));
        spinner.valueProperty().addListener((observable, previous, value) -> {
            if(value != null) {
                this.save(property, value);
            }
        });
        // Typed text is taken over when leaving the field and not only with return
        spinner.getEditor().focusedProperty().addListener((observable, was, focused) -> {
            if(!focused) {
                try {
                    spinner.getValueFactory().setValue(Integer.parseInt(spinner.getEditor().getText().trim()));
                }
                catch(NumberFormatException e) {
                    spinner.getEditor().setText(String.valueOf(spinner.getValue()));
                }
            }
        });
    }

    private Parent connection() {
        final GridPane grid = this.grid();
        this.commit(timeout, "connection.timeout.seconds");
        grid.addRow(0, new Label(Messages.get("Timeout in seconds")), timeout);
        this.commit(retries, "connection.retry");
        grid.addRow(1, new Label(Messages.get("Retries")), retries);
        proxy.setSelected(preferences.getBoolean("connection.proxy.enable"));
        proxy.setOnAction(event -> this.save("connection.proxy.enable", proxy.isSelected()));
        grid.add(proxy, 1, 2);
        return grid;
    }

    TextField getDownloadFolder() {
        return downloadFolder;
    }

    CheckBox getShowHidden() {
        return showHidden;
    }

    ComboBox<Protocol> getProtocol() {
        return protocol;
    }

    ComboBox<Choice> getDownloadAction() {
        return downloadAction;
    }

    ComboBox<Choice> getUploadAction() {
        return uploadAction;
    }

    ComboBox<Choice> getTransferType() {
        return transferType;
    }

    Spinner<Integer> getTimeout() {
        return timeout;
    }

    Spinner<Integer> getRetries() {
        return retries;
    }

    CheckBox getProxy() {
        return proxy;
    }
}
