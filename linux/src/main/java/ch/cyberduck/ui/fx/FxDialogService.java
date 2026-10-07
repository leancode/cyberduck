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

import ch.cyberduck.core.Credentials;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.LoginOptions;
import ch.cyberduck.core.threading.DefaultMainAction;
import ch.cyberduck.core.transfer.TransferAction;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

/**
 * Dialogs of JavaFX. All of them run on the JavaFX application thread and block the calling thread until closed.
 */
public class FxDialogService implements DialogService {

    private final FxController controller;

    public FxDialogService(final FxController controller) {
        this.controller = controller;
    }

    @Override
    public Credentials credentials(final Host bookmark, final String username, final String title, final String reason, final LoginOptions options) {
        return this.onApplicationThread(() -> {
            final Dialog<Credentials> dialog = new Dialog<>();
            this.owner(dialog);
            dialog.setTitle(StringUtils.defaultIfBlank(title, "Login"));
            final ButtonType login = new ButtonType(Messages.get("Login"), ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(login, ButtonType.CANCEL);

            final GridPane grid = new GridPane();
            grid.setHgap(8);
            grid.setVgap(8);
            grid.setPadding(new Insets(16));
            int row = 0;
            if(StringUtils.isNotBlank(reason)) {
                final Label label = new Label(reason);
                label.setWrapText(true);
                label.setMaxWidth(380);
                grid.add(label, 0, row++, 2, 1);
            }
            final TextField user = new TextField(StringUtils.defaultString(username));
            final PasswordField password = new PasswordField();
            final CheckBox save = new CheckBox(Messages.get("Save password"));
            save.setSelected(options.save());
            if(options.user()) {
                user.setPromptText(options.getUsernamePlaceholder());
                grid.addRow(row++, new Label(StringUtils.defaultIfBlank(options.getUsernamePlaceholder(), Messages.get("Username"))), user);
            }
            if(options.password() || options.token()) {
                password.setPromptText(options.getPasswordPlaceholder());
                grid.addRow(row++, new Label(StringUtils.defaultIfBlank(options.getPasswordPlaceholder(), Messages.get("Password"))), password);
            }
            if(options.keychain()) {
                grid.add(save, 1, row);
            }
            dialog.getDialogPane().setContent(grid);
            dialog.setOnShown(event -> (options.user() && StringUtils.isBlank(username) ? user : password).requestFocus());
            dialog.setResultConverter(type -> {
                if(type != login) {
                    return null;
                }
                final Credentials credentials = new Credentials(options.user() ? StringUtils.trim(user.getText()) : username);
                credentials.setPassword(password.getText());
                credentials.setSaved(options.keychain() && save.isSelected());
                return credentials;
            });
            return dialog.showAndWait().orElse(null);
        });
    }

    @Override
    public Confirmation confirm(final String title, final String message, final String defaultButton, final String cancelButton, final boolean suppressible) {
        return this.onApplicationThread(() -> {
            final ButtonType accept = new ButtonType(defaultButton, ButtonBar.ButtonData.OK_DONE);
            final ButtonType cancel = new ButtonType(cancelButton, ButtonBar.ButtonData.CANCEL_CLOSE);
            final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, accept, cancel);
            this.owner(alert);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.getDialogPane().setMinWidth(420);
            final CheckBox suppress = new CheckBox(Messages.get("Do not show again"));
            if(suppressible) {
                alert.getDialogPane().setExpandableContent(null);
                alert.getDialogPane().setContent(this.withCheckbox(message, suppress));
            }
            final boolean accepted = alert.showAndWait().filter(type -> type == accept).isPresent();
            return new Confirmation(accepted, suppressible && suppress.isSelected());
        });
    }

    @Override
    public String input(final String title, final String message, final String initial) {
        return this.onApplicationThread(() -> {
            final TextInputDialog dialog = new TextInputDialog(initial);
            this.owner(dialog);
            dialog.setTitle(title);
            dialog.setHeaderText(title);
            dialog.setContentText(message);
            dialog.setOnShown(event -> dialog.getEditor().selectAll());
            return dialog.showAndWait().orElse(null);
        });
    }

    @Override
    public TransferAction action(final String title, final String message, final List<TransferAction> actions) {
        return this.onApplicationThread(() -> {
            final Dialog<TransferAction> dialog = new Dialog<>();
            this.owner(dialog);
            dialog.setTitle(title);
            dialog.setHeaderText(title);
            final ButtonType accept = new ButtonType(Messages.get("Continue"), ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(accept, ButtonType.CANCEL);
            final Label text = new Label(message);
            text.setWrapText(true);
            text.setMaxWidth(420);
            final ComboBox<TransferAction> choices = new ComboBox<>();
            choices.getItems().setAll(actions);
            choices.setMaxWidth(Double.MAX_VALUE);
            final Label description = new Label();
            description.setWrapText(true);
            description.setMaxWidth(420);
            final javafx.util.Callback<javafx.scene.control.ListView<TransferAction>, ListCell<TransferAction>> cells = view -> new ListCell<>() {
                @Override
                protected void updateItem(final TransferAction item, final boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || null == item ? null : item.getTitle());
                }
            };
            choices.setCellFactory(cells);
            choices.setButtonCell(cells.call(null));
            choices.valueProperty().addListener((observable, previous, selected) -> description.setText(null == selected ? StringUtils.EMPTY : selected.getDescription()));
            choices.setValue(actions.isEmpty() ? null : actions.get(0));
            final javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10, text, choices, description);
            content.setPadding(new Insets(12));
            dialog.getDialogPane().setContent(content);
            dialog.setResultConverter(type -> type == accept ? choices.getValue() : null);
            return dialog.showAndWait().orElse(null);
        });
    }

    @Override
    public void error(final String title, final String message) {
        this.onApplicationThread(() -> {
            final Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
            this.owner(alert);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.getDialogPane().setMinWidth(420);
            alert.showAndWait();
            return null;
        });
    }

    private javafx.scene.Node withCheckbox(final String message, final CheckBox checkbox) {
        final Label label = new Label(message);
        label.setWrapText(true);
        label.setMaxWidth(400);
        final javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(12, label, checkbox);
        return box;
    }

    /**
     * Show above the window that has the focus or else any window that is showing
     */
    private void owner(final javafx.scene.control.Dialog<?> dialog) {
        Window owner = null;
        for(Window window : Window.getWindows()) {
            if(window.isShowing() && !(window.getScene() != null && window.getScene().getRoot() instanceof javafx.scene.control.DialogPane)) {
                if(window.isFocused()) {
                    owner = window;
                    break;
                }
                if(null == owner) {
                    owner = window;
                }
            }
        }
        if(owner != null) {
            dialog.initOwner(owner);
        }
    }

    private <T> T onApplicationThread(final Supplier<T> supplier) {
        final AtomicReference<T> result = new AtomicReference<>();
        controller.invoke(new DefaultMainAction() {
            @Override
            public void run() {
                result.set(supplier.get());
            }
        }, true);
        return result.get();
    }
}
