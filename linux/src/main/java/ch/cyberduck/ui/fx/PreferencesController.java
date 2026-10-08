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
import ch.cyberduck.core.ftp.FTPFileType;
import ch.cyberduck.core.local.ApplicationFinder;
import ch.cyberduck.core.local.ApplicationFinderFactory;
import ch.cyberduck.core.local.RevealServiceFactory;
import ch.cyberduck.core.preferences.LogDirectoryFinderFactory;
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
import javafx.scene.layout.VBox;
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

    /**
     * Choice for downloads that is not an action of the core, kept in the preference linux.download.compare
     */
    static final String COMPARE_TOOL = "compare-tool";

    private final Preferences preferences;
    private Stage stage;

    private final TextField downloadFolder = new TextField();
    private final CheckBox showHidden = new CheckBox(Messages.get("Show hidden files"));
    private final ComboBox<Protocol> protocol = new ComboBox<>();
    private final ComboBox<Choice> downloadAction = new ComboBox<>();
    private final ComboBox<Choice> uploadAction = new ComboBox<>();
    private final ComboBox<Choice> transferType = new ComboBox<>();
    private final ComboBox<Choice> language = new ComboBox<>();
    private final ComboBox<Choice> theme = new ComboBox<>();
    private final ComboBox<Choice> logLevel = new ComboBox<>();
    private final ComboBox<Choice> uploadSpeed = new ComboBox<>();
    private final ComboBox<Choice> downloadSpeed = new ComboBox<>();
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
            new Tab(Messages.get("Applications"), this.applications()),
            new Tab("FTP", this.ftp()),
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

        // Light or dark windows at once
        this.choose(theme, List.of(
            new Choice("", Messages.get("As the system")),
            new Choice("light", Messages.get("Light")),
            new Choice("dark", Messages.get("Dark"))), Theme.PROPERTY, "");
        theme.valueProperty().addListener((observable, previous, selected) -> Theme.refresh());
        grid.addRow(5, new Label(Messages.get("Appearance")), theme);

        // The language is read when the program starts
        final List<Choice> languages = new ArrayList<>();
        languages.add(new Choice("", Messages.get("System default")));
        for(String name : Languages.available(new File(ch.cyberduck.core.preferences.ApplicationResourcesFinderFactory.get().find().getAbsolute()))) {
            languages.add(new Choice(name, Languages.name(name)));
        }
        this.choose(language, languages, Languages.PROPERTY, "");
        grid.addRow(3, new Label(Messages.get("Language")), new HBox(8, language, new Label(Messages.get("Takes effect when the program is started again"))));

        // Everything the program does, for a report of a problem
        this.choose(logLevel, List.of(
            new Choice("ERROR", Messages.get("Errors only")),
            new Choice("WARN", Messages.get("Warnings")),
            new Choice("INFO", Messages.get("Information")),
            new Choice("DEBUG", Messages.get("Debug"))), "logging", "ERROR");
        logLevel.valueProperty().addListener((observable, previous, selected) -> {
            if(selected != null) {
                preferences.setLogging(selected.getValue());
            }
        });
        final Button showLog = new Button(Messages.get("Show Log File"));
        showLog.setOnAction(event -> RevealServiceFactory.get().reveal(LogDirectoryFinderFactory.get().find()));
        grid.addRow(4, new Label(Messages.get("Log")), new HBox(8, logLevel, showLog));
        return grid;
    }

    private static final class ProtocolListCell extends ListCell<Protocol> {
        @Override
        protected void updateItem(final Protocol item, final boolean empty) {
            super.updateItem(item, empty);
            setText(empty || null == item ? null : Messages.protocol(item));
        }
    }

    private List<Choice> actions(final Transfer.Type type) {
        final List<Choice> choices = new ArrayList<>();
        choices.add(new Choice("ask", Messages.get("Ask me what to do")));
        for(TransferAction action : TransferAction.forTransfer(type)) {
            // The core names it Compare, but it skips the files that did not change and shows nothing
            choices.add(new Choice(action.name(), TransferAction.comparison.equals(action) ? Messages.get("Skip files that did not change") : action.getTitle()));
        }
        if(Transfer.Type.download == type) {
            choices.add(new Choice(COMPARE_TOOL, Messages.get("Compare in a program")));
        }
        return choices;
    }

    private void choose(final ComboBox<Choice> box, final List<Choice> choices, final String property, final String fallback) {
        box.getItems().setAll(choices);
        final String current = "queue.download.action".equals(property) && preferences.getBoolean("linux.download.compare")
            ? COMPARE_TOOL : preferences.getProperty(property);
        box.setValue(choices.stream().filter(c -> c.getValue().equals(current)).findFirst()
            .orElse(choices.stream().filter(c -> c.getValue().equals(fallback)).findFirst().orElse(null)));
        box.valueProperty().addListener((observable, previous, selected) -> {
            if(selected != null) {
                if("queue.download.action".equals(property)) {
                    // The program for comparing is not known to the core, which is asked when the file exists
                    final boolean compare = COMPARE_TOOL.equals(selected.getValue());
                    preferences.setProperty("linux.download.compare", compare);
                    this.save(property, compare ? "ask" : selected.getValue());
                }
                else {
                    this.save(property, selected.getValue());
                }
            }
        });
    }

    /**
     * Limits in bytes per second
     */
    private List<Choice> speeds() {
        final List<Choice> choices = new ArrayList<>();
        choices.add(new Choice("-1", Messages.get("Unlimited")));
        for(int kilobytes : new int[]{50, 100, 250, 500, 1024, 2048, 5120, 10240}) {
            choices.add(new Choice(String.valueOf(kilobytes * 1024), String.format("%s/s", ch.cyberduck.core.formatter.SizeFormatterFactory.get().format(kilobytes * 1024L))));
        }
        return choices;
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
        // Applies to the transfers that are started afterwards
        this.choose(downloadSpeed, this.speeds(), "queue.download.bandwidth.bytes", "-1");
        grid.addRow(3, new Label(Messages.get("Limit download speed")), downloadSpeed);
        this.choose(uploadSpeed, this.speeds(), "queue.upload.bandwidth.bytes", "-1");
        grid.addRow(4, new Label(Messages.get("Limit upload speed")), uploadSpeed);
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

    private final ApplicationPicker defaultEditor = new ApplicationPicker(ApplicationFinderFactory.get(), Messages.get("System default"));
    private final CheckBox alwaysDefault = new CheckBox(Messages.get("Always use the default editor"));
    private final ApplicationPicker compareTool = new ApplicationPicker(ApplicationFinderFactory.get(), null);
    private final javafx.scene.control.TableView<String[]> editorTypes = new javafx.scene.control.TableView<>();

    /**
     * File extensions that have an editor of their own
     */
    private List<String> types() {
        final List<String> types = new ArrayList<>();
        for(String type : StringUtils.defaultString(preferences.getProperty("linux.editor.types")).split(",")) {
            if(StringUtils.isNotBlank(type)) {
                types.add(type.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return types;
    }

    private void showTypes() {
        editorTypes.getItems().clear();
        for(String type : this.types()) {
            editorTypes.getItems().add(new String[]{type, StringUtils.defaultString(preferences.getProperty("linux.editor." + type))});
        }
    }

    void setEditorForType(final String extension, final String command) {
        final String type = StringUtils.removeStart(extension.trim().toLowerCase(java.util.Locale.ROOT), ".");
        if(StringUtils.isBlank(type)) {
            return;
        }
        final List<String> types = this.types();
        if(StringUtils.isBlank(command)) {
            types.remove(type);
            preferences.deleteProperty("linux.editor." + type);
        }
        else {
            if(!types.contains(type)) {
                types.add(type);
            }
            preferences.setProperty("linux.editor." + type, command);
        }
        preferences.setProperty("linux.editor.types", String.join(",", types));
        preferences.save();
        this.showTypes();
    }

    private Parent applications() {
        final GridPane grid = this.grid();
        final ApplicationFinder finder = ApplicationFinderFactory.get();
        defaultEditor.setApplications(finder.findAll("file.txt"));
        defaultEditor.setCommand(preferences.getProperty("editor.bundleIdentifier"));
        defaultEditor.onChange(command -> this.save("editor.bundleIdentifier", command));
        grid.addRow(0, new Label(Messages.get("Default Editor")), defaultEditor);
        alwaysDefault.setSelected(preferences.getBoolean("editor.alwaysUseDefault"));
        alwaysDefault.setOnAction(event -> this.save("editor.alwaysUseDefault", alwaysDefault.isSelected()));
        grid.add(alwaysDefault, 1, 1);

        // An editor for each file type, for example txt, md or php
        final javafx.scene.control.TableColumn<String[], String> extension = new javafx.scene.control.TableColumn<>(Messages.get("File Type"));
        extension.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue()[0]));
        extension.setPrefWidth(110);
        final javafx.scene.control.TableColumn<String[], String> editor = new javafx.scene.control.TableColumn<>(Messages.get("Editor"));
        editor.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(finder.getDescription(row.getValue()[1]).getName()));
        editor.setPrefWidth(300);
        editorTypes.getColumns().setAll(extension, editor);
        editorTypes.setPrefHeight(120);
        editorTypes.setPlaceholder(new Label(Messages.get("No editors for file types")));
        this.showTypes();
        final Button add = new Button(Messages.get("Add…"));
        add.setOnAction(event -> this.addType());
        final Button remove = new Button(Messages.get("Remove"));
        remove.disableProperty().bind(editorTypes.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(event -> this.setEditorForType(editorTypes.getSelectionModel().getSelectedItem()[0], StringUtils.EMPTY));
        grid.add(new Label(Messages.get("Editor for File Type")), 0, 2);
        grid.add(new VBox(6, editorTypes, new HBox(8, add, remove)), 1, 2);

        compareTool.setApplications(CompareTools.installed());
        compareTool.setCommand(CompareTools.preferred().getIdentifier());
        compareTool.onChange(command -> this.save(CompareTools.PROPERTY, command));
        grid.addRow(3, new Label(Messages.get("Compare Files")), compareTool);
        GridPane.setHgrow(defaultEditor, Priority.ALWAYS);
        return grid;
    }

    private void addType() {
        final javafx.scene.control.Dialog<String[]> dialog = new javafx.scene.control.Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle(Messages.get("Editor for File Type"));
        final TextField extension = new TextField();
        extension.setPromptText("txt");
        final ApplicationPicker picker = new ApplicationPicker(ApplicationFinderFactory.get(), null);
        picker.setApplications(ApplicationFinderFactory.get().findAll("file.txt"));
        final GridPane content = this.grid();
        content.addRow(0, new Label(Messages.get("File Type")), extension);
        content.addRow(1, new Label(Messages.get("Editor")), picker);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK).disableProperty().bind(
            extension.textProperty().isEmpty());
        dialog.setResultConverter(type -> type == javafx.scene.control.ButtonType.OK ? new String[]{extension.getText(), picker.getCommand()} : null);
        dialog.showAndWait().ifPresent(result -> this.setEditorForType(result[0], result[1]));
    }

    private final ComboBox<String> encoding = new ComboBox<>();
    private final ComboBox<Choice> ftpTransferMode = new ComboBox<>();
    private final TextField asciiExtensions = new TextField();

    /**
     * Character set of file names and the type of the transfer, for the bookmarks that do not set their own
     */
    private Parent ftp() {
        final GridPane grid = this.grid();
        encoding.getItems().setAll(ConnectionDialog.ENCODINGS.stream().filter(e -> !e.isEmpty()).collect(java.util.stream.Collectors.toList()));
        encoding.setValue(StringUtils.defaultIfBlank(preferences.getProperty("browser.charset.encoding"), "UTF-8"));
        if(!encoding.getItems().contains(encoding.getValue())) {
            encoding.getItems().add(encoding.getValue());
        }
        encoding.valueProperty().addListener((observable, previous, selected) -> {
            if(selected != null) {
                this.save("browser.charset.encoding", selected);
            }
        });
        grid.addRow(0, new Label(Messages.get("Default Character Set")), encoding);
        this.choose(ftpTransferMode, List.of(
            new Choice(FTPFileType.BINARY, Messages.get("Binary")),
            new Choice(FTPFileType.ASCII, Messages.get("ASCII")),
            new Choice(FTPFileType.AUTO, Messages.get("Auto (by file type)"))), FTPFileType.MODE, FTPFileType.BINARY);
        grid.addRow(1, new Label(Messages.get("Transfer Mode")), ftpTransferMode);
        asciiExtensions.setText(StringUtils.defaultIfBlank(preferences.getProperty(FTPFileType.EXTENSIONS), FTPFileType.DEFAULT_EXTENSIONS));
        asciiExtensions.setPrefColumnCount(30);
        final Runnable saveExtensions = () -> this.save(FTPFileType.EXTENSIONS, asciiExtensions.getText().trim());
        asciiExtensions.setOnAction(event -> saveExtensions.run());
        asciiExtensions.focusedProperty().addListener((observable, was, focused) -> {
            if(!focused) {
                saveExtensions.run();
            }
        });
        grid.addRow(2, new Label(Messages.get("ASCII File Types")), asciiExtensions);
        final Label note = new Label(Messages.get("ASCII changes the line breaks of a file and with that its size. Binary keeps every byte. Applies to uploads, downloads are always binary. A bookmark can use another mode."));
        note.setWrapText(true);
        note.setMaxWidth(440);
        grid.add(note, 1, 3);
        GridPane.setHgrow(asciiExtensions, Priority.ALWAYS);
        return grid;
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

    ComboBox<Choice> getFtpTransferMode() {
        return ftpTransferMode;
    }

    ComboBox<String> getEncoding() {
        return encoding;
    }

    TextField getAsciiExtensions() {
        return asciiExtensions;
    }

    ApplicationPicker getDefaultEditor() {
        return defaultEditor;
    }

    ApplicationPicker getCompareTool() {
        return compareTool;
    }

    CheckBox getAlwaysDefault() {
        return alwaysDefault;
    }

    javafx.scene.control.TableView<String[]> getEditorTypes() {
        return editorTypes;
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

    ComboBox<Choice> getTheme() {
        return theme;
    }

    ComboBox<Choice> getLanguage() {
        return language;
    }

    ComboBox<Choice> getLogLevel() {
        return logLevel;
    }

    ComboBox<Choice> getDownloadSpeed() {
        return downloadSpeed;
    }

    ComboBox<Choice> getUploadSpeed() {
        return uploadSpeed;
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
