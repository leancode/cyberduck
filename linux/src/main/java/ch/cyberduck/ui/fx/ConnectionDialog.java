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
import ch.cyberduck.core.exception.HostParserException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

/**
 * Ask for the protocol and server to connect to. The result is a bookmark that is not saved.
 */
public class ConnectionDialog extends Dialog<Host> {
    private static final Logger log = LogManager.getLogger(ConnectionDialog.class);

    private final ProtocolFactory protocols;

    private final ComboBox<Protocol> protocol = new ComboBox<>();
    private final TextField server = new TextField();
    private final TextField port = new TextField();
    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final TextField path = new TextField();
    private final Label error = new Label();
    private final ButtonType connect = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);

    private Host host;

    public ConnectionDialog(final Window owner, final ProtocolFactory protocols) {
        this.protocols = protocols;
        this.initOwner(owner);
        this.setTitle("Open Connection");
        this.getDialogPane().getButtonTypes().addAll(connect, ButtonType.CANCEL);
        this.getDialogPane().setContent(this.build());

        // Validate when pressing connect and keep the dialog open to show the problem
        final Button button = this.getConnectButton();
        button.setDefaultButton(true);
        button.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                host = HostBuilder.fromFields(protocols, protocol.getValue(), server.getText(), port.getText(),
                    username.getText(), password.getText(), path.getText());
                error.setText(StringUtils.EMPTY);
            }
            catch(HostParserException | IllegalArgumentException e) {
                log.warn("Invalid input. {}", e.getMessage());
                error.setText(e.getMessage());
                event.consume();
            }
        });
        this.setResultConverter(type -> type == connect ? host : null);
        protocol.getItems().setAll(protocols.find());
        protocol.setCellFactory(list -> new ProtocolCell());
        protocol.setButtonCell(new ProtocolCell());
        protocol.valueProperty().addListener((observable, previous, selected) -> this.configure(selected));
        final Protocol preferred = protocols.forName("sftp");
        protocol.setValue(null != preferred ? preferred : protocol.getItems().stream().findFirst().orElse(null));
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
        grid.addRow(0, new Label("Protocol"), protocol);
        grid.addRow(1, new Label("Server"), server);
        grid.addRow(2, new Label("Port"), port);
        grid.addRow(3, new Label("Username"), username);
        grid.addRow(4, new Label("Password"), password);
        grid.addRow(5, new Label("Path"), path);
        error.setStyle("-fx-text-fill: red;");
        error.setWrapText(true);
        grid.add(error, 0, 6, 2, 1);
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
        error.setText(StringUtils.EMPTY);
    }

    ComboBox<Protocol> getProtocolBox() {
        return protocol;
    }

    TextField getServerField() {
        return server;
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
            setText(empty || null == item ? null : item.getDescription());
        }
    }
}
