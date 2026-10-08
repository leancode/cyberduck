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

import ch.cyberduck.core.local.Application;
import ch.cyberduck.core.local.ApplicationFinder;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * Choose a program from a list of the ones that are installed, or type the command of another one
 */
public final class ApplicationPicker extends HBox {

    private final ComboBox<Application> box = new ComboBox<>();
    private final Button other = new Button(Messages.get("Other…"));
    private final ApplicationFinder finder;
    private final Application none;
    private Consumer<String> listener = command -> {
        //
    };
    private boolean updating;

    /**
     * @param none Entry for the choice of no program of its own, shown first, or null for no such entry
     */
    public ApplicationPicker(final ApplicationFinder finder, final String none) {
        super(8);
        this.finder = finder;
        this.none = null == none ? null : new LinuxApplication(StringUtils.EMPTY, none);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setCellFactory(list -> new Cell());
        box.setButtonCell(new Cell());
        HBox.setHgrow(box, Priority.ALWAYS);
        box.valueProperty().addListener((observable, previous, selected) -> {
            if(!updating && selected != null) {
                listener.accept(selected.getIdentifier());
            }
        });
        other.setOnAction(event -> {
            final TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle(Messages.get("Other…"));
            dialog.setHeaderText(Messages.get("Enter the command of the program"));
            dialog.showAndWait().map(String::trim).filter(StringUtils::isNotEmpty).ifPresent(this::select);
        });
        this.setAlignment(Pos.CENTER_LEFT);
        this.getChildren().addAll(box, other);
    }

    private static final class Cell extends ListCell<Application> {
        @Override
        protected void updateItem(final Application item, final boolean empty) {
            super.updateItem(item, empty);
            setText(empty || null == item ? null : item.getName());
        }
    }

    /**
     * @param applications Programs to choose from
     */
    public void setApplications(final List<Application> applications) {
        updating = true;
        try {
            final Application current = box.getValue();
            box.getItems().clear();
            if(none != null) {
                box.getItems().add(none);
            }
            box.getItems().addAll(applications);
            box.setValue(current);
        }
        finally {
            updating = false;
        }
    }

    /**
     * Show the program with the command without telling the listener
     */
    public void setCommand(final String command) {
        updating = true;
        try {
            if(StringUtils.isBlank(command)) {
                box.setValue(none);
                return;
            }
            final Application application = finder.getDescription(command);
            if(!box.getItems().contains(application)) {
                box.getItems().add(application);
            }
            box.setValue(box.getItems().get(box.getItems().indexOf(application)));
        }
        finally {
            updating = false;
        }
    }

    private void select(final String command) {
        final Application application = finder.getDescription(command);
        if(!box.getItems().contains(application)) {
            box.getItems().add(application);
        }
        box.setValue(box.getItems().get(box.getItems().indexOf(application)));
    }

    /**
     * @return The command of the chosen program, empty for none
     */
    public String getCommand() {
        return null == box.getValue() ? StringUtils.EMPTY : StringUtils.defaultString(box.getValue().getIdentifier());
    }

    /**
     * @param listener Receives the command when the user chooses a program
     */
    public void onChange(final Consumer<String> listener) {
        this.listener = listener;
    }

    ComboBox<Application> getBox() {
        return box;
    }
}
