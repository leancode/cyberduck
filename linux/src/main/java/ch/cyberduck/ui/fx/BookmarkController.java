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
import ch.cyberduck.core.Local;
import ch.cyberduck.core.PasswordStoreFactory;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.UUIDRandomStringService;
import ch.cyberduck.core.preferences.PreferencesFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

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
    private final Button add = new Button(Messages.get("Add"));
    private final Button edit = new Button(Messages.get("Edit"));
    private final Button delete = new Button(Messages.get("Delete"));
    private final MenuButton more = new MenuButton(Messages.get("More"));
    private final BorderPane pane = new BorderPane();

    private ConnectionDialog dialog;
    private final CollectionListener<Host> listener = new CollectionListener<>() {
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
    };

    public BookmarkController(final BrowserController browser) {
        this(browser, BookmarkCollection.defaultCollection(), new FxDialogService(browser));
    }

    BookmarkController(final BrowserController browser, final AbstractHostCollection bookmarks, final DialogService dialogs) {
        this.browser = browser;
        this.bookmarks = bookmarks;
        this.dialogs = dialogs;

        list.setCellFactory(view -> new BookmarkCell());
        list.setPlaceholder(new Label(Messages.get("No bookmarks")));
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
        final MenuItem duplicateItem = new MenuItem(Messages.get("Duplicate Bookmark"));
        duplicateItem.setOnAction(event -> this.duplicate());
        duplicateItem.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        final Menu sortMenu = new Menu(Messages.get("Sort By"));
        for(String[] field : new String[][]{{"nickname", "Name"}, {"hostname", "Server"}, {"protocol", "Protocol"}}) {
            final MenuItem item = new MenuItem(Messages.get(field[1]));
            item.setOnAction(event -> this.sort(field[0]));
            sortMenu.getItems().add(item);
        }
        final MenuItem importFileZilla = new MenuItem(Messages.get("Import from FileZilla…"));
        importFileZilla.setOnAction(event -> this.importFileZilla());
        final MenuItem importSsh = new MenuItem(Messages.get("Import from SSH Config…"));
        importSsh.setOnAction(event -> this.importSshConfig());
        more.getItems().setAll(duplicateItem, sortMenu, new SeparatorMenuItem(), importFileZilla, importSsh);
        final ContextMenu menu = new ContextMenu();
        final MenuItem connectItem = new MenuItem(Messages.get("Connect"));
        connectItem.setOnAction(event -> this.connect());
        final MenuItem editItem = new MenuItem(Messages.get("Edit"));
        editItem.setOnAction(event -> this.edit());
        final MenuItem copyItem = new MenuItem(Messages.get("Duplicate Bookmark"));
        copyItem.setOnAction(event -> this.duplicate());
        final MenuItem deleteItem = new MenuItem(Messages.get("Delete"));
        deleteItem.setOnAction(event -> this.delete());
        menu.getItems().setAll(connectItem, editItem, copyItem, new SeparatorMenuItem(), deleteItem);
        list.setContextMenu(menu);
        final HBox buttons = new HBox(6, add, edit, delete, more);
        buttons.setPadding(new Insets(6));
        pane.setCenter(list);
        pane.setBottom(buttons);
        pane.setPrefWidth(230);

        bookmarks.addListener(listener);
        this.refresh();
    }

    /**
     * Stop listening to the collection when the window is closed
     */
    public void dispose() {
        bookmarks.removeListener(listener);
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
            final List<Host> sorted = new ArrayList<>(bookmarks);
            sorted.sort(comparator());
            items.setAll(sorted);
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

    static final String SORT = "linux.bookmarks.sort";

    private static Comparator<Host> comparator() {
        switch(StringUtils.defaultString(PreferencesFactory.get().getProperty(SORT))) {
            case "nickname":
                return AbstractHostCollection.SORT_BY_NICKNAME;
            case "hostname":
                return AbstractHostCollection.SORT_BY_HOSTNAME;
            case "protocol":
                return AbstractHostCollection.SORT_BY_PROTOCOL;
            default:
                // The order in which they were saved
                return (a, b) -> 0;
        }
    }

    /**
     * Show the bookmarks by name, server or protocol from now on
     */
    void sort(final String field) {
        PreferencesFactory.get().setProperty(SORT, field);
        PreferencesFactory.get().save();
        this.refresh();
    }

    /**
     * Save a copy of the selected bookmark with a name of its own
     */
    void duplicate() {
        final Host selected = list.getSelectionModel().getSelectedItem();
        if(null == selected) {
            return;
        }
        final Host copy = new Host(selected);
        copy.setUuid(new UUIDRandomStringService().random());
        copy.setNickname(String.format("%s %s", BookmarkNameProvider.toString(selected), Messages.get("copy")));
        copy.setCustom(new HashMap<>(selected.getCustom()));
        bookmarks.add(copy);
        list.getSelectionModel().select(copy);
    }

    /**
     * Add the servers from the site manager of FileZilla that are not bookmarks yet
     */
    void importFileZilla() {
        final Local file = this.choose(BookmarkImport.fileZillaFile(), "FileZilla");
        if(null == file) {
            return;
        }
        try {
            this.importHosts(BookmarkImport.fileZilla(file, PasswordStoreFactory.get()));
        }
        catch(ch.cyberduck.core.exception.BackgroundException e) {
            dialogs.error(Messages.get("Import from FileZilla…"), e.getDetail());
        }
    }

    /**
     * Add the hosts from the file of ssh that are not bookmarks yet
     */
    void importSshConfig() {
        final Local file = this.choose(BookmarkImport.sshConfigFile(), "SSH");
        if(null == file) {
            return;
        }
        try {
            this.importHosts(BookmarkImport.sshConfig(file, ProtocolFactory.get()));
        }
        catch(java.io.IOException e) {
            dialogs.error(Messages.get("Import from SSH Config…"), e.getMessage());
        }
    }

    /**
     * @return The usual file when there is one, otherwise the file the user chose
     */
    private Local choose(final Local usual, final String name) {
        if(usual.exists()) {
            return usual;
        }
        final FileChooser chooser = new FileChooser();
        chooser.setTitle(String.format("%s: %s", Messages.get("Choose the file"), name));
        final File chosen = chooser.showOpenDialog(browser.getStage());
        return null == chosen ? null : ch.cyberduck.core.LocalFactory.get(chosen.getAbsolutePath());
    }

    /**
     * @return Number of bookmarks that were added
     */
    int importHosts(final List<Host> found) {
        int added = 0;
        for(Host host : found) {
            if(bookmarks.stream().noneMatch(existing -> BookmarkImport.same(existing, host))) {
                bookmarks.add(host);
                added++;
            }
        }
        browser.message(String.format(Messages.get("Imported {0} bookmarks, {1} were there already").replace("{0}", "%1$d").replace("{1}", "%2$d"),
            added, found.size() - added));
        return added;
    }

    MenuButton getMore() {
        return more;
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
