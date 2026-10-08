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
import ch.cyberduck.core.ftp.FTPConnectMode;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.exception.HostParserException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TitledPane;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

/**
 * Ask for the protocol and server to connect to. The result is a bookmark that is not saved.
 */
public class ConnectionDialog extends Dialog<Host> {
    private static final Logger log = LogManager.getLogger(ConnectionDialog.class);

    private final ProtocolFactory protocols;
    private final boolean bookmark;

    private final TextField nickname = new TextField();
    private final ComboBox<Protocol> protocol = new ComboBox<>();
    private final TextField server = new TextField();
    private final TextField port = new TextField();
    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final CheckBox savePassword = new CheckBox(Messages.get("Save password in the keyring"));
    private final TextField path = new TextField();
    private final TextField privateKey = new TextField();
    private final Button chooseKey = new Button(Messages.get("Choose…"));
    private final HBox keyRow = new HBox(4, privateKey, chooseKey);
    private final Label privateKeyLabel = new Label(Messages.get("SSH Private Key"));
    private final CheckBox anonymous = new CheckBox(Messages.get("Anonymous Login"));
    /**
     * Character sets for the names of files. The empty text stands for the default of the preferences.
     */
    static final List<String> ENCODINGS = List.of("", "UTF-8", "ISO-8859-1", "ISO-8859-2", "ISO-8859-5", "ISO-8859-7", "ISO-8859-9",
        "ISO-8859-15", "windows-1250", "windows-1251", "windows-1252", "windows-1253", "windows-1254", "Shift_JIS", "EUC-JP", "ISO-2022-JP",
        "GBK", "GB18030", "Big5", "EUC-KR", "KOI8-R", "UTF-16");
    private final ComboBox<String> encoding = new ComboBox<>();
    private final ComboBox<FTPConnectMode> connectMode = new ComboBox<>();
    private final ComboBox<PreferencesController.Choice> transferMode = new ComboBox<>();
    private final Label encodingLabel = new Label(Messages.get("Encoding"));
    private final Label connectModeLabel = new Label(Messages.get("Connect Mode"));
    private final Label transferModeLabel = new Label(Messages.get("Transfer Mode"));
    private final TitledPane more = new TitledPane();
    private final Label error = new Label();
    private final ButtonType connect;

    private Host host;

    /**
     * Ask for a server to connect to
     */
    public ConnectionDialog(final Window owner, final ProtocolFactory protocols) {
        this(owner, protocols, false, null);
    }

    /**
     * @param bookmark True to create or edit a bookmark, which has a name and is saved, instead of connecting
     * @param initial  Values to start with or null
     */
    public ConnectionDialog(final Window owner, final ProtocolFactory protocols, final boolean bookmark, final Host initial) {
        this.protocols = protocols;
        this.bookmark = bookmark;
        this.connect = new ButtonType(Messages.get(bookmark ? "Save" : "Connect"), ButtonBar.ButtonData.OK_DONE);
        this.initOwner(owner);
        this.setTitle(bookmark ? (null == initial ? "New Bookmark" : "Edit Bookmark") : "Open Connection");
        this.getDialogPane().getButtonTypes().addAll(connect, ButtonType.CANCEL);
        this.getDialogPane().setContent(this.build());

        // Validate when pressing connect and keep the dialog open to show the problem
        final Button button = this.getConnectButton();
        button.setDefaultButton(true);
        button.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                host = HostBuilder.fromFields(protocols, protocol.getValue(), server.getText(), port.getText(),
                    username.getText(), password.getText(), path.getText());
                HostBuilder.identity(host, privateKey.getText());
                // Stored in the keyring once the login worked
                host.getCredentials().setSaved(!bookmark && savePassword.isSelected() && StringUtils.isNotEmpty(password.getText()));
                HostBuilder.options(host, anonymous.isSelected(), encoding.getValue(), connectMode.getValue(),
                    null == transferMode.getValue() ? null : transferMode.getValue().getValue());
                if(bookmark) {
                    host.setNickname(StringUtils.trimToNull(nickname.getText()));
                }
                error.setText(StringUtils.EMPTY);
            }
            catch(HostParserException | IllegalArgumentException e) {
                log.warn("Invalid input. {}", e.getMessage());
                error.setText(e.getMessage());
                event.consume();
            }
        });
        this.setResultConverter(type -> type == connect ? host : null);
        encoding.getItems().setAll(ENCODINGS);
        final javafx.util.Callback<javafx.scene.control.ListView<String>, ListCell<String>> encodings = list -> new ListCell<>() {
            @Override
            protected void updateItem(final String item, final boolean empty) {
                super.updateItem(item, empty);
                setText(empty || null == item ? null : item.isEmpty()
                    ? String.format("%s (%s)", Messages.get("Default"), PreferencesFactory.get().getProperty("browser.charset.encoding")) : item);
            }
        };
        encoding.setCellFactory(encodings);
        encoding.setButtonCell(encodings.call(null));
        encoding.setValue("");
        connectMode.getItems().setAll(FTPConnectMode.values());
        connectMode.setValue(FTPConnectMode.unknown);
        transferMode.getItems().setAll(
            new PreferencesController.Choice("", String.format("%s (%s)", Messages.get("Default"), StringUtils.defaultIfBlank(PreferencesFactory.get().getProperty(FTPFileType.MODE), FTPFileType.BINARY))),
            new PreferencesController.Choice(FTPFileType.BINARY, Messages.get("Binary")),
            new PreferencesController.Choice(FTPFileType.ASCII, Messages.get("ASCII")),
            new PreferencesController.Choice(FTPFileType.AUTO, Messages.get("Auto (by file type)")));
        transferMode.setValue(transferMode.getItems().get(0));
        anonymous.selectedProperty().addListener((observable, previous, selected) -> {
            username.setDisable(selected || !protocol.getValue().isUsernameConfigurable());
            password.setDisable(selected || !protocol.getValue().isPasswordConfigurable());
            if(selected) {
                username.setText(StringUtils.EMPTY);
                password.setText(StringUtils.EMPTY);
            }
        });
        protocol.getItems().setAll(protocols.find());
        protocol.setCellFactory(list -> new ProtocolCell());
        protocol.setButtonCell(new ProtocolCell());
        protocol.valueProperty().addListener((observable, previous, selected) -> this.configure(selected));
        final Protocol preferred = protocols.forName(PreferencesFactory.get().getProperty("connection.protocol.default"));
        protocol.setValue(null != preferred ? preferred : protocol.getItems().stream().findFirst().orElse(null));
        if(initial != null) {
            this.prefill(initial);
        }
    }

    /**
     * Start with the values of a bookmark. The protocol is selected first because that resets the other fields.
     */
    private void prefill(final Host initial) {
        protocol.setValue(initial.getProtocol());
        if(initial.getProtocol().isHostnameConfigurable()) {
            server.setText(initial.getHostname());
        }
        if(initial.getProtocol().isPortConfigurable()) {
            port.setText(String.valueOf(initial.getPort()));
        }
        username.setText(StringUtils.defaultString(initial.getCredentials().getUsername()));
        path.setText(StringUtils.defaultString(initial.getDefaultPath()));
        privateKey.setText(null == initial.getCredentials().getIdentity() ? StringUtils.EMPTY : initial.getCredentials().getIdentity().getAbsolute());
        nickname.setText(StringUtils.defaultString(initial.getNickname()));
        anonymous.setSelected(initial.getProtocol().isAnonymousConfigurable() && initial.getCredentials().isAnonymousLogin());
        final String charset = initial.getEncoding();
        encoding.setValue(null == charset || charset.equals(PreferencesFactory.get().getProperty("browser.charset.encoding")) ? "" : charset);
        if(initial.getProtocol().getType() == Protocol.Type.ftp) {
            connectMode.setValue(initial.getFTPConnectMode());
            final String mode = initial.getProperty(FTPFileType.MODE);
            transferMode.setValue(transferMode.getItems().stream().filter(c -> c.getValue().equals(StringUtils.defaultString(mode))).findFirst()
                .orElse(transferMode.getItems().get(0)));
        }
    }

    private GridPane build() {
        final GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));
        final ColumnConstraints labels = new ColumnConstraints();
        final ColumnConstraints fields = new ColumnConstraints(280);
        fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, fields);
        protocol.setMaxWidth(Double.MAX_VALUE);
        server.setPromptText("Server or URL");
        int row = 0;
        if(bookmark) {
            nickname.setPromptText("Name of the bookmark");
            grid.addRow(row++, new Label(Messages.get("Name")), nickname);
        }
        grid.addRow(row++, new Label(Messages.get("Protocol")), protocol);
        grid.addRow(row++, new Label(Messages.get("Server")), server);
        grid.addRow(row++, new Label(Messages.get("Port")), port);
        grid.addRow(row++, new Label(Messages.get("Username")), username);
        if(!bookmark) {
            // A bookmark stores no password
            grid.addRow(row++, new Label(Messages.get("Password")), password);
            savePassword.disableProperty().bind(password.textProperty().isEmpty().or(password.disableProperty()));
            grid.add(savePassword, 1, row++);
        }
        grid.add(anonymous, 1, row++);
        privateKey.setPromptText(Messages.get("Optional, instead of the password"));
        chooseKey.setOnAction(event -> {
            final javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle(Messages.get("SSH Private Key"));
            final java.io.File ssh = new java.io.File(System.getProperty("user.home"), ".ssh");
            if(ssh.isDirectory()) {
                chooser.setInitialDirectory(ssh);
            }
            final java.io.File selected = chooser.showOpenDialog(this.getDialogPane().getScene().getWindow());
            if(selected != null) {
                privateKey.setText(selected.getAbsolutePath());
            }
        });
        HBox.setHgrow(privateKey, Priority.ALWAYS);
        grid.addRow(row++, privateKeyLabel, keyRow);
        grid.addRow(row++, new Label(Messages.get("Path")), path);
        // Settings of a protocol that most connections do not need
        final GridPane options = new GridPane();
        options.setHgap(8);
        options.setVgap(8);
        options.setPadding(new Insets(8));
        options.addRow(0, encodingLabel, encoding);
        options.addRow(1, connectModeLabel, connectMode);
        options.addRow(2, transferModeLabel, transferMode);
        encoding.setMaxWidth(Double.MAX_VALUE);
        connectMode.setMaxWidth(Double.MAX_VALUE);
        transferMode.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(encoding, Priority.ALWAYS);
        more.setText(Messages.get("More Options"));
        more.setContent(options);
        more.setExpanded(false);
        grid.add(more, 0, row++, 2, 1);
        error.setStyle("-fx-text-fill: red;");
        error.setWrapText(true);
        grid.add(error, 0, row, 2, 1);
        return grid;
    }

    /**
     * Apply the defaults and limits of the selected protocol to the fields
     */
    private void configure(final Protocol selected) {
        if(null == selected) {
            return;
        }
        server.setDisable(!selected.isHostnameConfigurable());
        server.setText(StringUtils.EMPTY);
        server.setPromptText(selected.isHostnameConfigurable() ? "Server or URL" : selected.getDefaultHostname());
        port.setDisable(!selected.isPortConfigurable());
        port.setText(selected.isPortConfigurable() ? String.valueOf(selected.getDefaultPort()) : StringUtils.EMPTY);
        username.setDisable(!selected.isUsernameConfigurable());
        password.setDisable(!selected.isPasswordConfigurable());
        path.setPromptText(StringUtils.defaultString(selected.getDefaultPath()));
        anonymous.setVisible(selected.isAnonymousConfigurable());
        anonymous.setManaged(selected.isAnonymousConfigurable());
        anonymous.setSelected(false);
        for(javafx.scene.Node node : List.of(privateKeyLabel, keyRow, privateKey, chooseKey)) {
            node.setVisible(selected.isPrivateKeyConfigurable());
            node.setManaged(selected.isPrivateKeyConfigurable());
        }
        privateKey.setText(StringUtils.EMPTY);
        final boolean ftp = selected.getType() == Protocol.Type.ftp;
        for(javafx.scene.Node node : List.of(encodingLabel, encoding)) {
            node.setVisible(selected.isEncodingConfigurable());
            node.setManaged(selected.isEncodingConfigurable());
        }
        for(javafx.scene.Node node : List.of(connectModeLabel, connectMode, transferModeLabel, transferMode)) {
            node.setVisible(ftp);
            node.setManaged(ftp);
        }
        more.setVisible(selected.isEncodingConfigurable() || ftp);
        more.setManaged(selected.isEncodingConfigurable() || ftp);
        error.setText(StringUtils.EMPTY);
    }

    CheckBox getAnonymousBox() {
        return anonymous;
    }

    ComboBox<String> getEncodingBox() {
        return encoding;
    }

    ComboBox<FTPConnectMode> getConnectModeBox() {
        return connectMode;
    }

    ComboBox<PreferencesController.Choice> getTransferModeBox() {
        return transferMode;
    }

    TitledPane getMore() {
        return more;
    }

    TextField getNicknameField() {
        return nickname;
    }

    ComboBox<Protocol> getProtocolBox() {
        return protocol;
    }

    TextField getServerField() {
        return server;
    }

    TextField getPortField() {
        return port;
    }

    TextField getUsernameField() {
        return username;
    }

    CheckBox getSavePasswordBox() {
        return savePassword;
    }

    PasswordField getPasswordField() {
        return password;
    }

    TextField getPrivateKeyField() {
        return privateKey;
    }

    Button getChooseKeyButton() {
        return chooseKey;
    }

    TextField getPathField() {
        return path;
    }

    Button getConnectButton() {
        final Node node = this.getDialogPane().lookupButton(connect);
        return (Button) node;
    }

    private static final class ProtocolCell extends ListCell<Protocol> {
        @Override
        protected void updateItem(final Protocol item, final boolean empty) {
            super.updateItem(item, empty);
            setText(empty || null == item ? null : Messages.protocol(item));
        }
    }
}
