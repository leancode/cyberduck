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

import ch.cyberduck.core.AbstractHostCollection;
import ch.cyberduck.core.BookmarkCollection;
import ch.cyberduck.core.BookmarkNameProvider;
import ch.cyberduck.core.CollectionListener;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.ProtocolFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * List of saved connections. Double click opens the connection in the browser window.
 */
public class BookmarkController {
    private static final Logger log = LogManager.getLogger(BookmarkController.class);

    private final BrowserController browser;
    private final AbstractHostCollection bookmarks;
    private final DialogService dialogs;

    private final ObservableList<Host> items = FXCollections.observableArrayList();
    private final ListView<Host> list = new ListView<>(items);
    private final Button add = new Button("Add");
    private final Button edit = new Button("Edit");
    private final Button delete = new Button("Delete");
    private final BorderPane pane = new BorderPane();

    private ConnectionDialog dialog;

    public BookmarkController(final BrowserController browser) {
        this(browser, BookmarkCollection.defaultCollection(), new FxDialogService(browser));
    }

    BookmarkController(final BrowserController browser, final AbstractHostCollection bookmarks, final DialogService dialogs) {
        this.browser = browser;
        this.bookmarks = bookmarks;
        this.dialogs = dialogs;

        list.setCellFactory(view -> new BookmarkCell());
        list.setPlaceholder(new Label("No bookmarks"));
        list.setOnMouseClicked(event -> {
            if(event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                this.connect();
            }
        });
        add.setOnAction(event -> this.add());
        edit.setOnAction(event -> this.edit());
        delete.setOnAction(event -> this.delete());
        edit.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        final HBox buttons = new HBox(6, add, edit, delete);
        buttons.setPadding(new Insets(6));
        pane.setCenter(list);
        pane.setBottom(buttons);
        pane.setPrefWidth(230);

        bookmarks.addListener(new CollectionListener<Host>() {
            @Override
            public void collectionLoaded() {
                refresh();
            }

            @Override
            public void collectionItemAdded(final Host item) {
                refresh();
            }

            @Override
            public void collectionItemRemoved(final Host item) {
                refresh();
            }

            @Override
            public void collectionItemChanged(final Host item) {
                refresh();
            }
        });
        this.refresh();
    }

    public BorderPane getPane() {
        return pane;
    }

    ListView<Host> getList() {
        return list;
    }

    ConnectionDialog getDialog() {
        return dialog;
    }

    /**
     * Show the bookmarks as they are in the collection, keeping the selection. May be called from any thread.
     */
    private void refresh() {
        final Runnable action = () -> {
            final Host selected = list.getSelectionModel().getSelectedItem();
            items.setAll(bookmarks);
            if(selected != null && items.contains(selected)) {
                list.getSelectionModel().select(selected);
            }
        };
        if(Platform.isFxApplicationThread()) {
            action.run();
        }
        else {
            Platform.runLater(action);
        }
    }

    /**
     * Ask for the values of a new bookmark and save it
     */
    void add() {
        dialog = new ConnectionDialog(browser.getStage(), ProtocolFactory.get(), true, null);
        final Optional<Host> created = dialog.showAndWait();
        created.ifPresent(host -> {
            log.debug("Add bookmark {}", host);
            bookmarks.add(host);
            list.getSelectionModel().select(host);
        });
    }

    /**
     * Change the selected bookmark and save it
     */
    void edit() {
        final Host selected = list.getSelectionModel().getSelectedItem();
        if(null == selected) {
            return;
        }
        dialog = new ConnectionDialog(browser.getStage(), ProtocolFactory.get(), true, selected);
        dialog.showAndWait().ifPresent(changed -> {
            log.debug("Change bookmark {} to {}", selected, changed);
            HostBuilder.copy(changed, selected);
            bookmarks.collectionItemChanged(selected);
        });
    }

    /**
     * Remove the selected bookmark after confirmation
     */
    void delete() {
        final Host selected = list.getSelectionModel().getSelectedItem();
        if(null == selected) {
            return;
        }
        if(dialogs.confirm(String.format("Delete bookmark %s", BookmarkNameProvider.toString(selected)),
            "The bookmark will be removed. Files on the server are not affected.", "Delete", "Cancel", false).accepted()) {
            bookmarks.remove(selected);
        }
    }

    /**
     * Open the selected bookmark in the browser
     */
    void connect() {
        final Host selected = list.getSelectionModel().getSelectedItem();
        if(selected != null) {
            browser.mount(selected);
        }
    }

    private static final class BookmarkCell extends ListCell<Host> {
        private final Label name = new Label();
        private final Label url = new Label();
        private final VBox box = new VBox(2, name, url);

        private BookmarkCell() {
            name.setStyle("-fx-font-weight: bold;");
            url.setStyle("-fx-text-fill: gray;");
        }

        @Override
        protected void updateItem(final Host item, final boolean empty) {
            super.updateItem(item, empty);
            if(empty || null == item) {
                setGraphic(null);
                return;
            }
            name.setText(BookmarkNameProvider.toString(item));
            final String user = StringUtils.isNotBlank(item.getCredentials().getUsername()) ? String.format("%s@", item.getCredentials().getUsername()) : StringUtils.EMPTY;
            url.setText(String.format("%s://%s%s", item.getProtocol().getScheme().name(), user, item.getHostname()));
            setGraphic(box);
        }
    }
}
