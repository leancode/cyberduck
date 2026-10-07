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

import org.apache.commons.lang3.StringUtils;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

/**
 * Asks for the name of a new Cryptomator vault and its passphrase. The passphrase has to be typed twice because a
 * mistake cannot be corrected later, the files are lost without it.
 */
public final class VaultDialog extends Dialog<VaultDialog.Result> {

    /**
     * What was entered
     */
    public static final class Result {
        private final String name;
        private final String passphrase;
        private final boolean save;

        Result(final String name, final String passphrase, final boolean save) {
            this.name = name;
            this.passphrase = passphrase;
            this.save = save;
        }

        public String getName() {
            return name;
        }

        public String getPassphrase() {
            return passphrase;
        }

        /**
         * @return True to keep the passphrase in the keyring
         */
        public boolean isSave() {
            return save;
        }
    }

    private final TextField name = new TextField();
    private final PasswordField passphrase = new PasswordField();
    private final PasswordField confirm = new PasswordField();
    private final CheckBox save = new CheckBox(Messages.get("Save Passphrase"));

    public VaultDialog(final Window owner) {
        this.initOwner(owner);
        this.setTitle(Messages.get("Create Vault"));
        this.setHeaderText(Messages.get("Create Vault"));
        final ButtonType create = new ButtonType(Messages.get("Create"), ButtonBar.ButtonData.OK_DONE);
        this.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);
        name.setId("vault-name");
        passphrase.setId("vault-passphrase");
        confirm.setId("vault-confirm");
        final Label mismatch = new Label();
        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label(Messages.get("Name")), name);
        grid.addRow(1, new Label(Messages.get("Passphrase")), passphrase);
        grid.addRow(2, new Label(Messages.get("Confirm Passphrase")), confirm);
        grid.add(save, 1, 3);
        grid.add(mismatch, 1, 4);
        this.getDialogPane().setContent(grid);
        final Node button = this.getDialogPane().lookupButton(create);
        button.disableProperty().bind(Bindings.createBooleanBinding(
            () -> StringUtils.isBlank(name.getText()) || passphrase.getText().isEmpty() || !passphrase.getText().equals(confirm.getText()),
            name.textProperty(), passphrase.textProperty(), confirm.textProperty()));
        mismatch.textProperty().bind(Bindings.createStringBinding(
            () -> !confirm.getText().isEmpty() && !passphrase.getText().equals(confirm.getText()) ? Messages.get("Passphrases do not match") : StringUtils.EMPTY,
            passphrase.textProperty(), confirm.textProperty()));
        this.setResultConverter(type -> type == create ? new Result(StringUtils.trim(name.getText()), passphrase.getText(), save.isSelected()) : null);
    }

    TextField getName() {
        return name;
    }

    PasswordField getPassphrase() {
        return passphrase;
    }

    PasswordField getConfirm() {
        return confirm;
    }

    CheckBox getSave() {
        return save;
    }
}
