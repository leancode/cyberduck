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

import ch.cyberduck.core.AttributedList;
import ch.cyberduck.core.BookmarkNameProvider;
import ch.cyberduck.core.Cache;
import ch.cyberduck.core.DescriptiveUrl;
import ch.cyberduck.core.DescriptiveUrlBag;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.ListProgressListener;
import ch.cyberduck.core.Local;
import ch.cyberduck.core.LocalFactory;
import ch.cyberduck.core.NullFilter;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathCache;
import ch.cyberduck.core.LoginCallbackFactory;
import ch.cyberduck.core.Permission;
import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.SessionPoolFactory;
import ch.cyberduck.core.UserDateFormatterFactory;
import ch.cyberduck.core.formatter.SizeFormatterFactory;
import ch.cyberduck.core.pool.SessionPool;
import ch.cyberduck.core.local.ApplicationFinderFactory;
import ch.cyberduck.core.local.BrowserLauncherFactory;
import ch.cyberduck.core.local.Application;
import ch.cyberduck.core.editor.EditorFactory;
import ch.cyberduck.core.editor.Editor;
import ch.cyberduck.core.editor.DefaultEditorListener;
import ch.cyberduck.core.worker.LockVaultWorker;
import ch.cyberduck.core.worker.LoadVaultWorker;
import ch.cyberduck.core.worker.CreateVaultWorker;
import ch.cyberduck.core.vault.VaultVersion;
import ch.cyberduck.core.vault.VaultCredentials;
import ch.cyberduck.core.vault.RegistryVaultLoader;
import ch.cyberduck.core.features.Vault;
import ch.cyberduck.core.PasswordCallbackFactory;
import ch.cyberduck.core.preferences.Preferences;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.threading.DisconnectBackgroundAction;
import ch.cyberduck.core.threading.DefaultMainAction;
import ch.cyberduck.core.threading.WorkerBackgroundAction;
import ch.cyberduck.core.transfer.DownloadTransfer;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.TransferItem;
import ch.cyberduck.core.transfer.TransferOptions;
import ch.cyberduck.core.transfer.UploadTransfer;
import ch.cyberduck.core.exception.HostParserException;
import ch.cyberduck.core.features.Location;
import ch.cyberduck.core.worker.CreateDirectoryWorker;
import ch.cyberduck.core.worker.DeleteWorker;
import ch.cyberduck.core.worker.ListWorker;
import ch.cyberduck.core.worker.CopyWorker;
import ch.cyberduck.core.worker.TouchWorker;
import ch.cyberduck.core.transfer.SyncTransfer;
import ch.cyberduck.core.worker.MoveWorker;
import ch.cyberduck.core.worker.MountWorker;
import ch.cyberduck.ui.browser.DefaultBrowserFilter;
import ch.cyberduck.ui.browser.DownloadDirectoryFinder;
import ch.cyberduck.ui.browser.UploadDirectoryFinder;
import ch.cyberduck.ui.browser.UploadTargetFinder;
import ch.cyberduck.ui.comparator.FilenameComparator;
import ch.cyberduck.ui.comparator.OwnerComparator;
import ch.cyberduck.ui.comparator.PermissionsComparator;
import ch.cyberduck.ui.comparator.SizeComparator;
import ch.cyberduck.ui.comparator.TimestampComparator;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Browser window showing the contents of one directory of a connected host.
 */
public class BrowserController extends FxController {
    private static final Logger log = LogManager.getLogger(BrowserController.class);

    /**
     * Folders first, then by name, as long as the user has not chosen a column to sort by
     */
    private static final Comparator<Path> DEFAULT_ORDER = (a, b) -> {
        if(a.isDirectory() != b.isDirectory()) {
            return a.isDirectory() ? -1 : 1;
        }
        return new FilenameComparator(true).compare(a, b);
    };

    private final Preferences preferences = PreferencesFactory.get();

    private final Stage stage;
    private final TextField location = new TextField();
    private final TableView<Path> table = new TableView<>();
    private final ObservableList<Path> rows = FXCollections.observableArrayList();
    private final Label status = new Label();
    private final ToggleButton bookmarksToggle = new ToggleButton(Messages.get("Bookmarks"));
    private final Button connect = new Button(Messages.get("Connect"));
    private final Button back = new Button();
    private final Button forward = new Button();
    private final TextField quick = new TextField();
    private final TextField search = new TextField();
    private final javafx.scene.control.ListView<String> logView = new javafx.scene.control.ListView<>();
    private final javafx.collections.transformation.FilteredList<Path> filtered = new javafx.collections.transformation.FilteredList<>(rows, p -> true);
    private final Button up = new Button();
    private final Button refresh = new Button(Messages.get("Refresh"));
    private final Button download = new Button(Messages.get("Download"));
    private final Button upload = new Button(Messages.get("Upload"));
    private final Button transfers = new Button(Messages.get("Transfers"));
    private final Button newFolder = new Button(Messages.get("New Folder"));
    private final Button rename = new Button(Messages.get("Rename"));
    private final Button delete = new Button(Messages.get("Delete"));
    private final StringProperty summary = new SimpleStringProperty(StringUtils.EMPTY);

    private final Cache<Path> cache = new PathCache(preferences.getInteger("browser.cache.size"));
    private volatile VaultDialog vaultDialog;
    private final List<InfoController> infos = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final ListProgressListener listener = new ListProgressListener() {
        @Override
        public void chunk(final Path directory, final AttributedList<Path> list) {
            //
        }

        @Override
        public void message(final String message) {
            BrowserController.this.message(message);
        }
    };

    private SessionPool pool = SessionPool.DISCONNECTED;
    private Path workdir;
    /**
     * Directory shown in the table
     */
    private Path rendered;
    private final javafx.beans.property.ObjectProperty<Path> renderedProperty = new javafx.beans.property.SimpleObjectProperty<>();
    /**
     * Directory requested but not yet listed. Used to ignore a listing that has been superseded.
     */
    private Path pending;
    private ConnectionDialog connection;
    private MenuBar menu;
    private final DialogService dialogs = new FxDialogService(this);
    /**
     * Select this file after the next listing
     */
    private Path selectAfterRender;
    private BookmarkController bookmarks;
    /**
     * Previously shown directories for the back button
     */
    private final Deque<Path> history = new ArrayDeque<>();
    /**
     * Directories left with the back button, for the forward button
     */
    private final Deque<Path> forwards = new ArrayDeque<>();

    public BrowserController(final Stage stage) {
        this.stage = stage;
        this.stage.setTitle(preferences.getProperty("application.name"));
        this.stage.setScene(new Scene(this.build(), 1100, 650));
        // Files that are dragged out of the window are downloaded from the moment they leave it
        this.stage.getScene().setOnDragExited(event -> this.startPendingDrag());
    }

    private BorderPane build() {
        final BorderPane root = new BorderPane();
        bookmarks = new BookmarkController(this);
        bookmarksToggle.setSelected(true);
        this.contextMenus();
        bookmarksToggle.setOnAction(event -> root.setLeft(bookmarksToggle.isSelected() ? bookmarks.getPane() : null));
        connect.setOnAction(event -> this.connect());
        back.setOnAction(event -> this.back());
        forward.setOnAction(event -> this.forward());
        up.setOnAction(event -> this.up());
        refresh.setOnAction(event -> this.reload());
        download.setOnAction(event -> this.download());
        upload.setOnAction(event -> this.upload());
        transfers.setOnAction(event -> TransferController.get().show());
        newFolder.setOnAction(event -> this.newFolder());
        rename.setOnAction(event -> this.rename());
        delete.setOnAction(event -> this.delete());
        newFolder.disableProperty().bind(Bindings.createBooleanBinding(() -> null == rendered, renderedProperty));
        rename.disableProperty().bind(Bindings.size(table.getSelectionModel().getSelectedItems()).isNotEqualTo(1));
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        upload.disableProperty().bind(Bindings.createBooleanBinding(() -> null == rendered, renderedProperty));
        download.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        location.setOnAction(event -> this.go(location.getText()));
        HBox.setHgrow(location, Priority.ALWAYS);
        final HBox top = new HBox(8, bookmarksToggle, connect, back, forward, up, refresh, download, upload, newFolder, delete, transfers, location, quick, search);
        top.setPadding(new Insets(8));
        top.setAlignment(Pos.CENTER_LEFT);
        // Never cut the labels of the buttons. The path field gives way instead and the window cannot get narrower than the toolbar.
        for(Region button : new Region[]{bookmarksToggle, connect, back, forward, up, refresh, download, upload, newFolder, delete, transfers}) {
            button.setMinWidth(Region.USE_PREF_SIZE);
        }
        back.setGraphic(Icons.back());
        back.setTooltip(new Tooltip(Messages.get("Back")));
        back.setAccessibleText(Messages.get("Back"));
        // Symbols instead of words keep the toolbar short; the word shows when the pointer rests on the symbol.
        // Rename is in the menus, on the right mouse button and on F2.
        this.symbol(refresh, Icons.reload(), "Refresh");
        this.symbol(download, Icons.download(), "Download");
        this.symbol(upload, Icons.upload(), "Upload");
        this.symbol(newFolder, Icons.newFolder(), "New Folder");
        this.symbol(delete, Icons.trash(), "Delete");
        forward.setGraphic(Icons.forward());
        forward.setTooltip(new Tooltip(Messages.get("Forward")));
        forward.setAccessibleText(Messages.get("Forward"));
        quick.setPromptText(Messages.get("Quick Connect"));
        quick.setPrefWidth(120);
        quick.setMinWidth(60);
        quick.setOnAction(event -> this.quickConnect(quick.getText()));
        search.setPromptText(Messages.get("Search"));
        search.setPrefWidth(110);
        search.setMinWidth(60);
        search.textProperty().addListener((observable, previous, text) -> this.filter(text));
        up.setGraphic(Icons.up());
        up.setTooltip(new Tooltip(Messages.get("Enclosing Folder")));
        up.setAccessibleText(Messages.get("Enclosing Folder"));
        location.setPrefWidth(160);
        location.setMinWidth(80);
        stage.setOnShown(event -> stage.setMinWidth(Math.min(
            top.minWidth(-1) + stage.getWidth() - stage.getScene().getWidth(), Screen.getPrimary().getVisualBounds().getWidth())));

        // The filter hides files, the sorted view follows the column headers
        final javafx.collections.transformation.SortedList<Path> sorted = new javafx.collections.transformation.SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setPlaceholder(new Label());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(view -> {
            final TableRow<Path> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if(event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    this.open(row.getItem());
                }
            });
            // A right click selects the row, like in a file manager, and shows what can be done with it
            row.setOnContextMenuRequested(event -> {
                if(!row.isEmpty()) {
                    if(!row.isSelected()) {
                        table.getSelectionModel().clearAndSelect(row.getIndex());
                    }
                    this.showMenu(rowMenu, row.getItem(), event);
                }
            });
            // The files that are dragged out are downloaded while they are on their way
            row.setOnDragDetected(event -> this.dragOut(event, row));
            // Files dropped on a folder go into the folder
            row.setOnDragOver(event -> this.acceptDrag(event, !row.isEmpty() && row.getItem().isDirectory() ? row.getItem() : null));
            row.setOnDragDropped(event -> this.drop(event, !row.isEmpty() && row.getItem().isDirectory() ? row.getItem() : null));
            row.setOnDragDone(event -> {
                pendingDrag = null;
                dragged = null;
            });
            return row;
        });
        // The empty area of the listing offers what can be done in the folder that is shown
        table.setOnContextMenuRequested(event -> this.showMenu(emptyMenu, null, event));
        // Files dropped anywhere else go into the folder that is shown
        table.setOnDragOver(event -> this.acceptDrag(event, null));
        table.setOnDragDropped(event -> this.drop(event, null));
        table.setOnKeyPressed(event -> {
            switch(event.getCode()) {
                case ENTER: {
                    final Path selected = table.getSelectionModel().getSelectedItem();
                    if(selected != null) {
                        this.open(selected);
                    }
                    break;
                }
                case DELETE:
                    if(!table.getSelectionModel().isEmpty()) {
                        this.delete();
                    }
                    break;
                case F2:
                    if(table.getSelectionModel().getSelectedItems().size() == 1) {
                        this.rename();
                    }
                    break;
                case BACK_SPACE:
                    this.up();
                    break;
                default:
                    return;
            }
            event.consume();
        });
        table.getColumns().add(this.column("Filename", 360, new FilenameComparator(true), p -> {
            final String name = p.getName();
            return p.isDirectory() ? String.format("%s%s", name, Path.DELIMITER) : name;
        }, Pos.CENTER_LEFT, p -> p.isDirectory() ? Icons.folder() : Icons.file(p.getName())));
        table.getColumns().add(this.column("Size", 90, new SizeComparator(true), p -> {
            if(p.isDirectory() || p.attributes().getSize() < 0) {
                return StringUtils.EMPTY;
            }
            return SizeFormatterFactory.get().format(p.attributes().getSize());
        }, Pos.CENTER_RIGHT));
        table.getColumns().add(this.column("Modified", 170, new TimestampComparator(true), p -> {
            if(p.attributes().getModificationDate() <= 0) {
                return StringUtils.EMPTY;
            }
            return UserDateFormatterFactory.get().getMediumFormat(p.attributes().getModificationDate());
        }, Pos.CENTER_LEFT));
        table.getColumns().add(this.column("Permissions", 110, new PermissionsComparator(true), p -> {
            final Permission permission = p.attributes().getPermission();
            if(null == permission || Permission.EMPTY.equals(permission)) {
                return StringUtils.EMPTY;
            }
            return permission.getSymbol();
        }, Pos.CENTER_LEFT));
        table.getColumns().add(this.column("Owner", 100, new OwnerComparator(true), p -> StringUtils.defaultString(p.attributes().getOwner()), Pos.CENTER_LEFT));

        // Activity message while busy, otherwise the number of items in the directory
        status.textProperty().bind(Bindings.createStringBinding(
            () -> StringUtils.isBlank(this.messageProperty().get()) ? summary.get() : this.messageProperty().get(),
            this.messageProperty(), summary));
        final HBox bottom = new HBox(status);
        bottom.setPadding(new Insets(4, 8, 4, 8));

        root.setCenter(table);
        root.setLeft(bookmarks.getPane());
        root.setTop(new VBox(this.menu(), top));
        // The log of the connection sits above the status line when it is shown
        logView.setItems(this.getTranscriptLines());
        logView.setPrefHeight(150);
        logView.setFixedCellSize(18);
        this.getTranscriptLines().addListener((javafx.collections.ListChangeListener<String>) change -> {
            if(logView.isVisible() && !logView.getItems().isEmpty()) {
                logView.scrollTo(logView.getItems().size() - 1);
            }
        });
        logView.setVisible(false);
        logView.setManaged(false);
        root.setBottom(new VBox(logView, bottom));
        this.updateNavigation();
        return root;
    }

    private void symbol(final Button button, final javafx.scene.Node icon, final String label) {
        final String text = Messages.get(label);
        button.setText(null);
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(text));
        button.setAccessibleText(text);
    }

    private TableColumn<Path, Path> column(final String title, final double width, final Comparator<Path> comparator,
                                           final Function<Path, String> text, final Pos alignment) {
        return this.column(title, width, comparator, text, alignment, null);
    }

    private TableColumn<Path, Path> column(final String title, final double width, final Comparator<Path> comparator,
                                           final Function<Path, String> text, final Pos alignment, final Function<Path, javafx.scene.Node> icon) {
        final TableColumn<Path, Path> column = new TableColumn<>(Messages.get(title));
        column.setPrefWidth(width);
        column.setComparator(comparator);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(final Path item, final boolean empty) {
                super.updateItem(item, empty);
                setAlignment(alignment);
                setText(empty || null == item ? null : text.apply(item));
                setGraphic(empty || null == item || null == icon ? null : icon.apply(item));
            }
        });
        return column;
    }

    private final Map<Path, Editor> editors = new java.util.concurrent.ConcurrentHashMap<>();
    private final ContextMenu rowMenu = new ContextMenu();
    private final Menu editWithRow = new Menu();
    private final ContextMenu emptyMenu = new ContextMenu();
    private MenuItem lockVaultRow;
    private MenuItem openRow;
    private MenuItem editRow;
    private MenuItem compareRow;
    private MenuItem downloadAsRow;
    private MenuItem terminalRow;
    private MenuItem emptyTerminal;

    private MenuItem item(final String text, final Runnable action) {
        final MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    /**
     * What can be done with a file or folder, and in the folder that is shown
     */
    private void contextMenus() {
        openRow = this.item(Messages.get("Open"), () -> {
            final Path selected = table.getSelectionModel().getSelectedItem();
            if(selected != null) {
                this.open(selected);
            }
        });
        editWithRow.setText(Messages.get("Edit With"));
        lockVaultRow = this.item(Messages.get("Unlock Vault"), this::lockUnlockVault);
        rowMenu.getItems().setAll(
            openRow,
            this.item(Messages.get("Download"), this::download),
            this.item(Messages.get("Download To…"), this::downloadTo),
            downloadAsRow = this.item(Messages.get("Download As…"), this::downloadAs),
            editRow = this.item(Messages.get("Edit"), this::edit),
            compareRow = this.item(Messages.get("Compare") + "…", this::compare),
            editWithRow,
            new SeparatorMenuItem(),
            this.item(Messages.get("Get Info"), this::info),
            this.item(Messages.get("Copy URL"), this::copyUrl),
            new SeparatorMenuItem(),
            this.item(Messages.get("Cut"), this::cutFiles),
            this.item(Messages.get("Copy"), this::copyFiles),
            this.item(Messages.get("Paste"), this::paste),
            new SeparatorMenuItem(),
            this.item(Messages.get("Rename"), this::rename),
            this.item(Messages.get("Duplicate File") + "…", this::duplicate),
            this.item(Messages.get("Delete"), this::delete),
            new SeparatorMenuItem(),
            lockVaultRow,
            this.item(Messages.get("Synchronize") + "…", this::synchronize),
            new SeparatorMenuItem(),
            this.item(Messages.get("New Folder"), this::newFolder),
            this.item(Messages.get("New File"), this::newFile),
            this.item(Messages.get("Upload") + "…", this::upload),
            terminalRow = this.item(Messages.get("Open in Terminal"), this::openTerminal),
            this.item(Messages.get("Refresh"), this::reload));
        emptyMenu.getItems().setAll(
            this.item(Messages.get("Upload") + "…", this::upload),
            this.item(Messages.get("New Folder"), this::newFolder),
            this.item(Messages.get("New File"), this::newFile),
            this.item(Messages.get("Paste"), this::paste),
            emptyTerminal = this.item(Messages.get("Open in Terminal"), this::openTerminal),
            new SeparatorMenuItem(),
            this.item(Messages.get("Refresh"), this::reload),
            this.item(Messages.get("Synchronize") + "…", this::synchronize),
            this.item(Messages.get("Create Vault") + "…", this::createVault));
    }

    private void showMenu(final ContextMenu menu, final Path selected, final ContextMenuEvent event) {
        event.consume();
        if(!this.isMounted()) {
            return;
        }
        final boolean terminal = TerminalLauncher.isSupported(pool.getHost());
        terminalRow.setVisible(terminal);
        emptyTerminal.setVisible(terminal);
        if(selected != null) {
            downloadAsRow.setVisible(table.getSelectionModel().getSelectedItems().size() == 1);
            editRow.setVisible(selected.isFile());
            compareRow.setVisible(selected.isFile());
            editWithRow.setVisible(selected.isFile());
            if(selected.isFile()) {
                editWithRow.getItems().clear();
                for(Application application : ApplicationFinderFactory.get().findAll(selected.getName())) {
                    editWithRow.getItems().add(this.item(application.getName(), () -> this.edit(application, selected)));
                }
                if(editWithRow.getItems().isEmpty()) {
                    final MenuItem none = new MenuItem(Messages.get("None"));
                    none.setDisable(true);
                    editWithRow.getItems().add(none);
                }
            }
            lockVaultRow.setVisible(selected.isDirectory());
            lockVaultRow.setText(Messages.get(pool.getVaultRegistry().contains(selected) ? "Lock Vault" : "Unlock Vault"));
        }
        rowMenu.hide();
        emptyMenu.hide();
        menu.show(table, event.getScreenX(), event.getScreenY());
    }

    ContextMenu getRowMenu() {
        return rowMenu;
    }

    ContextMenu getEmptyMenu() {
        return emptyMenu;
    }

    private MenuBar menu() {
        final MenuItem newBrowser = new MenuItem(Messages.get("New Browser"));
        newBrowser.setAccelerator(KeyCombination.keyCombination("Shortcut+N"));
        newBrowser.setOnAction(event -> MainController.get().newBrowser(null));
        final MenuItem open = new MenuItem(Messages.get("Open Connection…"));
        open.setAccelerator(KeyCombination.keyCombination("Shortcut+O"));
        open.setOnAction(event -> this.connect());
        final MenuItem disconnect = new MenuItem(Messages.get("Disconnect"));
        disconnect.setOnAction(event -> this.unmount(() -> {
            //
        }));
        disconnect.disableProperty().bind(Bindings.createBooleanBinding(() -> null == rendered, renderedProperty));
        final MenuItem closeWindow = new MenuItem(Messages.get("Close Window"));
        closeWindow.setAccelerator(KeyCombination.keyCombination("Shortcut+W"));
        closeWindow.setOnAction(event -> this.close());
        final MenuItem edit = new MenuItem(Messages.get("Edit"));
        edit.setAccelerator(KeyCombination.keyCombination("Shortcut+E"));
        edit.setOnAction(event -> this.edit());
        edit.disableProperty().bind(Bindings.createBooleanBinding(
            () -> table.getSelectionModel().getSelectedItems().stream().noneMatch(Path::isFile), table.getSelectionModel().getSelectedItems()));
        final MenuItem compareItem = new MenuItem(Messages.get("Compare") + "…");
        compareItem.setOnAction(event -> this.compare());
        compareItem.disableProperty().bind(Bindings.createBooleanBinding(
            () -> table.getSelectionModel().getSelectedItems().stream().noneMatch(Path::isFile), table.getSelectionModel().getSelectedItems()));
        final MenuItem duplicate = new MenuItem(Messages.get("Duplicate File") + "…");
        duplicate.setAccelerator(KeyCombination.keyCombination("Shortcut+D"));
        duplicate.setOnAction(event -> this.duplicate());
        duplicate.disableProperty().bind(Bindings.size(table.getSelectionModel().getSelectedItems()).isNotEqualTo(1));
        final MenuItem synchronize = new MenuItem(Messages.get("Synchronize") + "…");
        synchronize.setOnAction(event -> this.synchronize());
        synchronize.disableProperty().bind(Bindings.createBooleanBinding(() -> null == rendered, renderedProperty));
        final MenuItem createVault = new MenuItem(Messages.get("Create Vault") + "…");
        createVault.setOnAction(event -> this.createVault());
        createVault.disableProperty().bind(Bindings.createBooleanBinding(() -> null == rendered, renderedProperty));
        final MenuItem lockVault = new MenuItem(Messages.get("Unlock Vault"));
        lockVault.setOnAction(event -> this.lockUnlockVault());
        lockVault.disableProperty().bind(Bindings.createBooleanBinding(
            () -> null == table.getSelectionModel().getSelectedItem() || !table.getSelectionModel().getSelectedItem().isDirectory(),
            table.getSelectionModel().selectedItemProperty()));
        // The text follows the state of the selected folder
        table.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) ->
            lockVault.setText(Messages.get(null != selected && this.isMounted() && pool.getVaultRegistry().contains(selected) ? "Lock Vault" : "Unlock Vault")));
        final MenuItem info = new MenuItem(Messages.get("Get Info"));
        info.setAccelerator(KeyCombination.keyCombination("Shortcut+I"));
        info.setOnAction(event -> this.info());
        info.disableProperty().bind(Bindings.size(table.getSelectionModel().getSelectedItems()).isNotEqualTo(1));
        final MenuItem preferencesItem = new MenuItem(Messages.get("Preferences…"));
        preferencesItem.setAccelerator(KeyCombination.keyCombination("Shortcut+,"));
        preferencesItem.setOnAction(event -> PreferencesController.get().show());
        final MenuItem quit = new MenuItem(Messages.get("Quit"));
        quit.setAccelerator(KeyCombination.keyCombination("Shortcut+Q"));
        quit.setOnAction(event -> MainController.get().quit());
        final MenuItem showTransfers = new MenuItem(Messages.get("Transfers"));
        showTransfers.setAccelerator(KeyCombination.keyCombination("Shortcut+T"));
        showTransfers.setOnAction(event -> TransferController.get().show());
        final MenuItem newFolderItem = this.item(Messages.get("New Folder") + "…", this::newFolder);
        newFolderItem.setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+N"));
        newFolderItem.disableProperty().bind(newFolder.disableProperty());
        final MenuItem newFileItem = this.item(Messages.get("New File") + "…", this::newFile);
        newFileItem.setAccelerator(KeyCombination.keyCombination("Shortcut+Alt+N"));
        newFileItem.disableProperty().bind(newFolder.disableProperty());
        final MenuItem downloadToItem = this.item(Messages.get("Download To…"), this::downloadTo);
        downloadToItem.disableProperty().bind(download.disableProperty());
        final MenuItem downloadAsItem = this.item(Messages.get("Download As…"), this::downloadAs);
        downloadAsItem.disableProperty().bind(Bindings.size(table.getSelectionModel().getSelectedItems()).isNotEqualTo(1));
        final MenuItem openWeb = this.item(Messages.get("Open in Web Browser"), this::openInWebBrowser);
        openWeb.disableProperty().bind(newFolder.disableProperty());
        final MenuItem terminalItem = this.item(Messages.get("Open in Terminal"), this::openTerminal);
        terminalItem.setAccelerator(KeyCombination.keyCombination("Shortcut+Alt+T"));
        terminalItem.disableProperty().bind(newFolder.disableProperty());
        final MenuItem cutItem = this.item(Messages.get("Cut"), this::cutFiles);
        cutItem.setAccelerator(KeyCombination.keyCombination("Shortcut+X"));
        cutItem.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        final MenuItem copyItem = this.item(Messages.get("Copy"), this::copyFiles);
        copyItem.setAccelerator(KeyCombination.keyCombination("Shortcut+C"));
        copyItem.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        final MenuItem pasteItem = this.item(Messages.get("Paste"), this::paste);
        pasteItem.setAccelerator(KeyCombination.keyCombination("Shortcut+V"));
        pasteItem.disableProperty().bind(newFolder.disableProperty());
        final MenuItem copyUrlItem = this.item(Messages.get("Copy URL"), this::copyUrl);
        copyUrlItem.setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+C"));
        copyUrlItem.disableProperty().bind(newFolder.disableProperty());
        final MenuItem selectAll = this.item(Messages.get("Select All"), () -> table.getSelectionModel().selectAll());
        selectAll.setAccelerator(KeyCombination.keyCombination("Shortcut+A"));
        final CheckMenuItem hidden = new CheckMenuItem(Messages.get("Show Hidden Files"));
        hidden.setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+."));
        hidden.setSelected(preferences.getBoolean("browser.showHidden"));
        hidden.setOnAction(event -> {
            preferences.setProperty("browser.showHidden", hidden.isSelected());
            if(null != rendered) {
                this.render(rendered);
            }
        });
        final CheckMenuItem showLog = new CheckMenuItem(Messages.get("Show Log"));
        showLog.setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+L"));
        showLog.setOnAction(event -> {
            logView.setVisible(showLog.isSelected());
            logView.setManaged(showLog.isSelected());
        });
        final MenuItem refreshItem = this.item(Messages.get("Refresh"), this::reload);
        refreshItem.setAccelerator(KeyCombination.keyCombination("Shortcut+R"));
        refreshItem.disableProperty().bind(refresh.disableProperty());
        final MenuItem findItem = this.item(Messages.get("Search"), () -> search.requestFocus());
        findItem.setAccelerator(KeyCombination.keyCombination("Shortcut+F"));
        final MenuItem backItem = this.item(Messages.get("Back"), this::back);
        backItem.setAccelerator(KeyCombination.keyCombination("Alt+Left"));
        backItem.disableProperty().bind(back.disableProperty());
        final MenuItem forwardItem = this.item(Messages.get("Forward"), this::forward);
        forwardItem.setAccelerator(KeyCombination.keyCombination("Alt+Right"));
        forwardItem.disableProperty().bind(forward.disableProperty());
        final MenuItem upItem = this.item(Messages.get("Enclosing Folder"), this::up);
        upItem.setAccelerator(KeyCombination.keyCombination("Alt+Up"));
        upItem.disableProperty().bind(up.disableProperty());
        final MenuItem goTo = this.item(Messages.get("Go to Folder…"), () -> {
            location.requestFocus();
            location.selectAll();
        });
        goTo.setAccelerator(KeyCombination.keyCombination("Shortcut+L"));
        goTo.disableProperty().bind(location.disableProperty());
        final MenuItem quickItem = this.item(Messages.get("Quick Connect"), () -> quick.requestFocus());
        quickItem.setAccelerator(KeyCombination.keyCombination("Shortcut+K"));
        final String website = preferences.getProperty("website.help");
        menu = new MenuBar(
            new Menu(Messages.get("File"), null, newBrowser, open, quickItem, disconnect, new SeparatorMenuItem(), newFolderItem, newFileItem, new SeparatorMenuItem(), downloadToItem, downloadAsItem, new SeparatorMenuItem(), openWeb, terminalItem, new SeparatorMenuItem(), edit, compareItem, duplicate, synchronize, new SeparatorMenuItem(), createVault, lockVault, new SeparatorMenuItem(), info, preferencesItem, new SeparatorMenuItem(), closeWindow, quit),
            new Menu(Messages.get("Edit"), null, cutItem, copyItem, pasteItem, new SeparatorMenuItem(), copyUrlItem, selectAll),
            new Menu(Messages.get("View"), null, hidden, showLog, refreshItem, findItem),
            new Menu(Messages.get("Go"), null, backItem, forwardItem, upItem, goTo),
            new Menu(Messages.get("Window"), null, showTransfers),
            new Menu(Messages.get("Help"), null,
                this.item(Messages.get("Cyberduck Help"), () -> this.browse(website)),
                this.item(Messages.get("Report a Bug"), () -> this.browse(MessageFormat.format(preferences.getProperty("website.bug"), Version.get()))),
                this.item(Messages.get("License"), () -> this.browse(preferences.getProperty("website.license"))),
                this.item(Messages.get("Acknowledgments"), () -> this.browse(preferences.getProperty("website.acknowledgments"))),
                this.item(Messages.get("Privacy Policy"), () -> this.browse(preferences.getProperty("website.privacypolicy"))),
                new SeparatorMenuItem(),
                this.item(Messages.get("About Cyberduck"), this::about)));
        return menu;
    }

    private void browse(final String url) {
        BrowserLauncherFactory.get().open(url);
    }

    /**
     * Name, version and where to find the documentation
     */
    void about() {
        final javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle(Messages.get("About Cyberduck"));
        alert.setHeaderText(String.format("%s %s", preferences.getProperty("application.name"), Version.get()));
        alert.setContentText(String.format("%s%n%n%s%n%s", Messages.get("Cloud storage browser for FTP, SFTP, WebDAV, Amazon S3 and more."),
            preferences.getProperty("website.home"), preferences.getProperty("website.linux")));
        alert.showAndWait();
    }

    /**
     * Download the selected files, open them in the editor for their type and upload them again when they are saved
     */
    void edit() {
        for(Path selected : new ArrayList<>(table.getSelectionModel().getSelectedItems())) {
            if(selected.isFile()) {
                this.edit(EditorFactory.getEditor(selected.getName()), selected);
            }
        }
    }

    void edit(final Application application, final Path file) {
        if(!this.isMounted()) {
            return;
        }
        final Editor editor = editors.computeIfAbsent(file, f -> EditorFactory.instance().create(pool.getHost(), f, this));
        this.background(new WorkerBackgroundAction<>(this, pool, editor.open(application, () -> editors.remove(file),
            new DefaultEditorListener(this, pool, editor, () -> {
                this.message(String.format("%s saved", file.getName()));
                this.invoke(new DefaultMainAction() {
                    @Override
                    public void run() {
                        reload();
                    }
                });
            }))));
    }

    /**
     * Ask for a name and copy the selected file or folder on the server with that name
     */
    void duplicate() {
        final Path selected = table.getSelectionModel().getSelectedItem();
        if(null == selected || !this.isMounted() || null == workdir) {
            return;
        }
        final String name = dialogs.input(Messages.get("Duplicate File"), Messages.get("Enter the name of the copy"),
            String.format("%s %s", selected.getName(), Messages.get("copy")));
        if(StringUtils.isBlank(name)) {
            return;
        }
        this.duplicate(selected, new Path(selected.getParent(), StringUtils.trim(name), selected.getType()));
    }

    void duplicate(final Path source, final Path target) {
        if(cache.get(source.getParent()).contains(target)
            && DialogService.Confirmation.YES != dialogs.confirm(Messages.get("File exists"),
            String.format(Messages.get("The file {0} exists. Do you want to overwrite it?").replace("{0}", "%s"), target.getName()),
            Messages.get("Overwrite"), Messages.get("Cancel"), false)) {
            return;
        }
        this.copy(Collections.singletonMap(source, target), target);
    }

    /**
     * Copy files on the server
     *
     * @param files  Source and destination of each
     * @param select Select this file when the copy is done or null
     */
    void copy(final Map<Path, Path> files, final Path select) {
        // A stateful protocol needs a connection of its own, because the one of the browser is busy listing
        final SessionPool destination = pool.getHost().getProtocol().getStatefulness() == Protocol.Statefulness.stateful
            ? SessionPoolFactory.create(this, pool.getHost()) : pool;
        this.background(new WorkerBackgroundAction<>(this, pool,
            new CopyWorker(files, destination, cache, this, LoginCallbackFactory.get(this)) {
                @Override
                public void cleanup(final Map<Path, Path> result) {
                    super.cleanup(result);
                    if(destination != pool) {
                        destination.shutdown();
                    }
                    if(result != null && !result.isEmpty()) {
                        selectAfterRender = select;
                    }
                    reload();
                }
            }));
    }

    /**
     * Move files on the server
     *
     * @param files  Source and destination of each
     * @param select Select this file when the move is done or null
     */
    void move(final Map<Path, Path> files, final Path select) {
        // Moving needs a second connection to the same server to borrow while the first is in use
        final SessionPool target = pool.getHost().getProtocol().getStatefulness() == Protocol.Statefulness.stateful
            ? SessionPoolFactory.create(this, pool.getHost()) : pool;
        this.background(new WorkerBackgroundAction<>(this, pool,
            new MoveWorker(files, target, cache, this, LoginCallbackFactory.get(this)) {
                @Override
                public void cleanup(final Map<Path, Path> result) {
                    super.cleanup(result);
                    if(target != pool) {
                        target.shutdown();
                    }
                    for(Path file : files.keySet()) {
                        if(file.isDirectory()) {
                            cache.invalidate(file);
                        }
                    }
                    if(result != null && !result.isEmpty()) {
                        selectAfterRender = select;
                    }
                    reload();
                }
            }));
    }

    /**
     * Ask for a folder on this computer and make it the same as the selected folder on the server, or the folder that
     * is shown. The transfer asks what to do, upload, download or both.
     */
    void synchronize() {
        if(!this.isMounted() || null == workdir) {
            return;
        }
        final DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(Messages.get("Synchronize"));
        final File selected = chooser.showDialog(stage);
        if(selected != null) {
            this.synchronize(selected);
        }
    }

    void synchronize(final File folder) {
        final Path selected = table.getSelectionModel().getSelectedItem();
        final Path remote = null != selected && selected.isDirectory() ? selected : workdir;
        final Local local = LocalFactory.get(folder.getAbsolutePath());
        log.debug("Synchronize {} with {}", remote, local);
        this.transfer(new SyncTransfer(pool.getHost(), new TransferItem(remote, local)));
    }

    /**
     * Ask for a name and a passphrase, then make a Cryptomator vault in the folder that is shown
     */
    void createVault() {
        if(!this.isMounted() || null == workdir) {
            return;
        }
        final VaultDialog dialog = new VaultDialog(stage);
        vaultDialog = dialog;
        final java.util.Optional<VaultDialog.Result> result;
        try {
            result = dialog.showAndWait();
        }
        finally {
            vaultDialog = null;
        }
        if(!result.isPresent()) {
            return;
        }
        final Path folder = new Path(workdir, result.get().getName(), EnumSet.of(Path.Type.directory));
        final VaultCredentials passphrase = new VaultCredentials(result.get().getPassphrase()).setSaved(result.get().isSave());
        final VaultVersion metadata = new VaultVersion(VaultVersion.Type.valueOf(preferences.getProperty("cryptomator.vault.default")));
        this.background(new WorkerBackgroundAction<>(this, pool, new CreateVaultWorker(Location.unknown.getIdentifier(), folder, passphrase, metadata) {
            @Override
            public void cleanup(final Vault vault) {
                super.cleanup(vault);
                if(vault != null) {
                    selectAfterRender = folder;
                }
                reload();
            }
        }));
    }

    /**
     * Lock the selected folder when it is an open vault, otherwise ask for the passphrase and unlock it
     */
    void lockUnlockVault() {
        final Path selected = table.getSelectionModel().getSelectedItem();
        if(null == selected || !selected.isDirectory() || !this.isMounted()) {
            return;
        }
        if(pool.getVaultRegistry().contains(selected)) {
            this.background(new WorkerBackgroundAction<>(this, pool, new LockVaultWorker(pool.getVaultRegistry(), selected) {
                @Override
                public void cleanup(final Path locked) {
                    super.cleanup(locked);
                    cache.invalidate(selected);
                    reload();
                }
            }));
        }
        else {
            this.background(new WorkerBackgroundAction<>(this, pool, new LoadVaultWorker(new RegistryVaultLoader(pool.getVaultRegistry(),
                PasswordCallbackFactory.get(this)), selected) {
                @Override
                public void cleanup(final Vault vault) {
                    super.cleanup(vault);
                    cache.invalidate(selected);
                    reload();
                }
            }));
        }
    }

    /**
     * @return True when the folder is a vault that is unlocked
     */
    boolean isUnlocked(final Path directory) {
        return this.isMounted() && pool.getVaultRegistry().contains(directory);
    }

    VaultDialog getVaultDialog() {
        return vaultDialog;
    }

    /**
     * Show the properties of the selected file or folder
     */
    void info() {
        final Path selected = table.getSelectionModel().getSelectedItem();
        if(selected != null && this.isMounted()) {
            final InfoController window = new InfoController(this, pool, cache, selected);
            infos.add(window);
            window.show();
        }
    }

    /**
     * @return The windows with properties opened so far
     */
    List<InfoController> getInfos() {
        return infos;
    }

    MenuBar getMenuBar() {
        return menu;
    }

    /**
     * Disconnect, then close the window. The application ends when it was the last window.
     */
    public void close() {
        this.unmount(() -> {
            this.dispose();
            MainController.get().closed(this);
        });
    }

    /**
     * Release the window
     */
    void dispose() {
        bookmarks.dispose();
        stage.hide();
    }

    public void show() {
        stage.show();
    }

    public Stage getStage() {
        return stage;
    }

    javafx.scene.control.ListView<String> getLogView() {
        return logView;
    }

    Button getForwardButton() {
        return forward;
    }

    TextField getQuick() {
        return quick;
    }

    TextField getSearch() {
        return search;
    }

    Button getRefresh() {
        return refresh;
    }

    TableView<Path> getTable() {
        return table;
    }

    public Path getWorkdir() {
        return workdir;
    }

    /**
     * @return Directory currently shown in the table or null
     */
    Path getRendered() {
        return rendered;
    }

    public boolean isMounted() {
        return pool != SessionPool.DISCONNECTED;
    }

    /**
     * Open connection in browser. Closes the current connection first.
     *
     * @param bookmark Bookmark
     */
    public void mount(final Host bookmark) {
        log.debug("Mount session for {}", bookmark);
        this.unmount(() -> {
            pool = SessionPoolFactory.create(this, bookmark, SessionPoolFactory.Usage.browser);
            this.background(new WorkerBackgroundAction<>(this, pool, new MountWorker(bookmark, cache, listener) {
                @Override
                public void cleanup(final Path home, final ch.cyberduck.core.exception.BackgroundException failure) {
                    super.cleanup(home, failure);
                    if(null == home) {
                        log.warn("Mount of {} failed", bookmark);
                        unmount(() -> {
                            //
                        });
                    }
                    else {
                        stage.setTitle(BookmarkNameProvider.toString(bookmark));
                        setWorkdir(home);
                    }
                }
            }));
        });
    }

    /**
     * Close the connection if any
     *
     * @param disconnected Run after the connection has been closed
     */
    public void unmount(final Runnable disconnected) {
        if(!this.isMounted()) {
            disconnected.run();
            return;
        }
        final SessionPool closing = pool;
        this.background(new DisconnectBackgroundAction(this, closing) {
            @Override
            public void cleanup() {
                super.cleanup();
                closing.shutdown();
                BrowserController.this.pool = SessionPool.DISCONNECTED;
                cache.clear();
                workdir = null;
                pending = null;
                history.clear();
                forwards.clear();
                updateNavigation();
                rendered = null;
                renderedProperty.set(null);
                rows.clear();
                summary.set(StringUtils.EMPTY);
                location.clear();
                stage.setTitle(preferences.getProperty("application.name"));
                disconnected.run();
            }
        });
    }

    /**
     * Show the contents of a directory and remember the previous directory for the back button. Lists the directory
     * from the server if it is not in the cache.
     *
     * @param directory Folder to display
     */
    public void setWorkdir(final Path directory) {
        this.navigate(directory, true);
    }

    private void navigate(final Path directory, final boolean remember) {
        log.debug("Set working directory to {}", directory);
        pending = directory;
        final Path previous = workdir;
        if(cache.isValid(directory)) {
            this.show(directory, previous, remember);
            return;
        }
        this.background(new WorkerBackgroundAction<>(this, pool, new ListWorker(cache, directory, listener) {
            @Override
            public void cleanup(final AttributedList<Path> list) {
                super.cleanup(list);
                if(!directory.equals(pending)) {
                    log.debug("Ignore superseded listing of {}", directory);
                    return;
                }
                if(AttributedList.<Path>emptyList() == list) {
                    // Listing failed. Keep showing the previous directory.
                    pending = null;
                    location.setText(null == previous ? StringUtils.EMPTY : previous.getAbsolute());
                }
                else {
                    show(directory, previous, remember);
                }
            }
        }));
    }

    private void show(final Path directory, final Path previous, final boolean remember) {
        if(remember && null != previous && !previous.equals(directory)) {
            history.push(previous);
            forwards.clear();
        }
        pending = null;
        workdir = directory;
        location.setText(directory.getAbsolute());
        this.render(directory);
        this.updateNavigation();
    }

    private void updateNavigation() {
        back.setDisable(history.isEmpty());
        forward.setDisable(forwards.isEmpty());
        up.setDisable(null == workdir || workdir.isRoot());
        refresh.setDisable(null == workdir);
        location.setDisable(null == workdir);
    }

    /**
     * Open a folder. Files are not opened yet.
     */
    void open(final Path file) {
        if(file.isDirectory()) {
            this.setWorkdir(file);
        }
    }

    /**
     * Show the parent directory
     */
    void up() {
        if(null != workdir && !workdir.isRoot()) {
            this.setWorkdir(workdir.getParent());
        }
    }

    /**
     * Show the previously shown directory
     */
    void back() {
        if(!history.isEmpty()) {
            if(null != workdir) {
                forwards.push(workdir);
            }
            this.navigate(history.pop(), false);
            this.updateNavigation();
        }
    }

    /**
     * Show the directory that the back button left
     */
    void forward() {
        if(!forwards.isEmpty()) {
            if(null != workdir) {
                history.push(workdir);
            }
            this.navigate(forwards.pop(), false);
            this.updateNavigation();
        }
    }

    /**
     * Open the connection for a URL or for a server name, using the default protocol when there is no scheme
     */
    void quickConnect(final String input) {
        final String typed = StringUtils.trimToEmpty(input);
        if(typed.isEmpty()) {
            return;
        }
        if(typed.contains("://")) {
            this.open(typed);
        }
        else {
            final Protocol preferred = ProtocolFactory.get().forName(preferences.getProperty("connection.protocol.default"));
            this.open(String.format("%s://%s", null == preferred ? "sftp" : preferred.getScheme().name(), typed));
        }
        quick.clear();
    }

    /**
     * Show only the files with a name that contains the text
     */
    void filter(final String text) {
        final String needle = StringUtils.lowerCase(StringUtils.trimToEmpty(text));
        filtered.setPredicate(needle.isEmpty() ? p -> true : p -> StringUtils.lowerCase(p.getName()).contains(needle));
        this.summarize();
    }

    private void summarize() {
        summary.set(null == rendered ? StringUtils.EMPTY : filtered.size() == rows.size()
            ? String.format("%d items", rows.size()) : String.format("%d of %d items", filtered.size(), rows.size()));
    }

    /**
     * Show the directory typed in the location field. Relative paths are relative to the current directory.
     */
    void go(final String input) {
        String typed = StringUtils.trimToEmpty(input);
        if(typed.isEmpty() || null == workdir) {
            return;
        }
        if(typed.length() > 1) {
            typed = StringUtils.removeEnd(typed, String.valueOf(Path.DELIMITER));
        }
        this.setWorkdir(typed.startsWith(String.valueOf(Path.DELIMITER))
            ? new Path(typed, EnumSet.of(Path.Type.directory))
            : new Path(workdir, typed, EnumSet.of(Path.Type.directory)));
    }

    /**
     * Open the connection that a URL describes, for example {@code sftp://user@example.net/home}
     */
    void open(final String url) {
        try {
            this.mount(HostBuilder.fromUrl(ProtocolFactory.get(), url));
        }
        catch(HostParserException e) {
            dialogs.error("Invalid URL", String.format("%s%n%n%s", url, e.getDetail()));
        }
    }

    /**
     * Ask for a server and open the connection
     */
    void connect() {
        connection = new ConnectionDialog(stage, ProtocolFactory.get());
        connection.showAndWait().ifPresent(this::mount);
    }

    /**
     * @return The connection dialog that was opened last
     */
    BookmarkController getBookmarks() {
        return bookmarks;
    }

    ConnectionDialog getConnectionDialog() {
        return connection;
    }

    /**
     * Download the selected files to the download folder
     */
    void download() {
        if(!this.isMounted()) {
            return;
        }
        final List<Path> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if(selected.isEmpty()) {
            return;
        }
        final Host host = pool.getHost();
        final Local target = new DownloadDirectoryFinder().find(host);
        // When files that exist should be compared, they go to the program for comparing and only the others are downloaded
        final List<Path> download = new ArrayList<>();
        for(Path file : selected) {
            if(preferences.getBoolean("linux.download.compare") && file.isFile() && LocalFactory.get(target, file.getName()).exists()) {
                this.compare(file, LocalFactory.get(target, file.getName()));
            }
            else {
                download.add(file);
            }
        }
        if(download.isEmpty()) {
            return;
        }
        log.debug("Download {} to {}", download, target);
        this.transfer(new DownloadTransfer(host, download.stream()
            .map(file -> new TransferItem(file, LocalFactory.get(target, file.getName()))).collect(Collectors.toList())));
    }

    /**
     * Compare the selected files with the files of the same name in the folder for downloads. Ask for the file when
     * there is none.
     */
    void compare() {
        if(!this.isMounted()) {
            return;
        }
        final Local target = new DownloadDirectoryFinder().find(pool.getHost());
        for(Path selected : new ArrayList<>(table.getSelectionModel().getSelectedItems())) {
            if(!selected.isFile()) {
                continue;
            }
            Local local = LocalFactory.get(target, selected.getName());
            if(!local.exists()) {
                final FileChooser chooser = new FileChooser();
                chooser.setTitle(String.format(Messages.get("Compare %s with"), selected.getName()));
                if(new File(target.getAbsolute()).isDirectory()) {
                    chooser.setInitialDirectory(new File(target.getAbsolute()));
                }
                final File file = chooser.showOpenDialog(stage);
                if(null == file) {
                    continue;
                }
                local = LocalFactory.get(file.getAbsolutePath());
            }
            this.compare(selected, local);
        }
    }

    /**
     * Download the file from the server again to a place of its own, without touching the file on this computer, and
     * show both in the program for comparing
     */
    void compare(final Path remote, final Local local) {
        final Application tool = CompareTools.preferred();
        if(Application.notfound.equals(tool)) {
            dialogs.error(Messages.get("Compare"), Messages.get("No program to compare files was found. Install one such as Meld or choose one in the preferences."));
            return;
        }
        final Local copy;
        try {
            final String extension = FilenameUtils.getExtension(remote.getName());
            copy = LocalFactory.get(Files.createTempDirectory("cyberduck-compare").toString(),
                String.format("%s (server)%s", FilenameUtils.getBaseName(remote.getName()), StringUtils.isEmpty(extension) ? "" : "." + extension));
        }
        catch(IOException e) {
            dialogs.error(Messages.get("Compare"), e.getMessage());
            return;
        }
        log.debug("Compare {} with {} using {}", local, remote, tool);
        TransferController.get().start(new DownloadTransfer(pool.getHost(), remote, copy), new TransferOptions(), this, completed -> {
            if(completed.isComplete()) {
                new LinuxApplicationLauncher().open(tool, List.of(local.getAbsolute(), copy.getAbsolute()));
            }
        });
    }

    /**
     * Ask for a name and create a folder in the folder that is shown
     */
    void newFolder() {
        if(!this.isMounted() || null == workdir) {
            return;
        }
        final String name = dialogs.input("New Folder", "Enter the name of the new folder", "untitled folder");
        if(StringUtils.isBlank(name)) {
            return;
        }
        final Path folder = new Path(workdir, StringUtils.trim(name), EnumSet.of(Path.Type.directory));
        this.background(new WorkerBackgroundAction<>(this, pool, new CreateDirectoryWorker(folder, Location.unknown.getIdentifier()) {
            @Override
            public void cleanup(final Path created) {
                super.cleanup(created);
                if(created != null) {
                    selectAfterRender = folder;
                }
                reload();
            }
        }));
    }

    /**
     * Ask for a name and create an empty file in the folder that is shown
     */
    void newFile() {
        if(!this.isMounted() || null == workdir) {
            return;
        }
        final String name = dialogs.input(Messages.get("New File"), Messages.get("Enter the name of the new file"), "untitled.txt");
        if(StringUtils.isBlank(name)) {
            return;
        }
        final Path file = new Path(workdir, StringUtils.trim(name), EnumSet.of(Path.Type.file));
        if(cache.get(workdir).contains(file)) {
            dialogs.error(Messages.get("New File"), String.format(Messages.get("The file {0} exists.").replace("{0}", "%s"), file.getName()));
            return;
        }
        this.background(new WorkerBackgroundAction<>(this, pool, new TouchWorker(file) {
            @Override
            public void cleanup(final Path created) {
                super.cleanup(created);
                if(created != null) {
                    selectAfterRender = file;
                }
                reload();
            }
        }));
    }

    /**
     * Ask for a folder on this computer and download the selected files into it
     */
    void downloadTo() {
        if(!this.isMounted() || table.getSelectionModel().isEmpty()) {
            return;
        }
        final DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(Messages.get("Download To…"));
        final Local suggested = new DownloadDirectoryFinder().find(pool.getHost());
        if(suggested.exists()) {
            chooser.setInitialDirectory(new File(suggested.getAbsolute()));
        }
        final File folder = chooser.showDialog(stage);
        if(folder != null) {
            this.downloadTo(new ArrayList<>(table.getSelectionModel().getSelectedItems()), folder);
        }
    }

    void downloadTo(final List<Path> files, final File folder) {
        final Local target = LocalFactory.get(folder.getAbsolutePath());
        this.transfer(new DownloadTransfer(pool.getHost(), files.stream()
            .map(file -> new TransferItem(file, LocalFactory.get(target, file.getName()))).collect(Collectors.toList())));
    }

    /**
     * Ask for the name on this computer and download the selected file with that name
     */
    void downloadAs() {
        if(!this.isMounted() || table.getSelectionModel().getSelectedItems().size() != 1) {
            return;
        }
        final Path file = table.getSelectionModel().getSelectedItem();
        final FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("Download As…"));
        chooser.setInitialFileName(file.getName());
        final Local suggested = new DownloadDirectoryFinder().find(pool.getHost());
        if(suggested.exists()) {
            chooser.setInitialDirectory(new File(suggested.getAbsolute()));
        }
        final File chosen = chooser.showSaveDialog(stage);
        if(chosen != null) {
            this.downloadAs(file, chosen);
        }
    }

    void downloadAs(final Path file, final File chosen) {
        this.transfer(new DownloadTransfer(pool.getHost(), Collections.singletonList(new TransferItem(file, LocalFactory.get(chosen.getAbsolutePath())))));
    }

    /**
     * The selected files, or else the folder that is shown
     */
    private List<Path> selectedOrWorkdir() {
        final List<Path> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if(selected.isEmpty() && null != workdir) {
            selected.add(workdir);
        }
        return selected;
    }

    /**
     * Ask the server for the addresses of the files and hand them over in the order of the files
     */
    private void urls(final List<Path> files, final java.util.function.Consumer<List<DescriptiveUrlBag>> done) {
        if(!this.isMounted() || files.isEmpty()) {
            return;
        }
        final DescriptiveUrlBag[] results = new DescriptiveUrlBag[files.size()];
        final java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(files.size());
        for(int i = 0; i < files.size(); i++) {
            final int index = i;
            this.background(new WorkerBackgroundAction<>(this, pool, new InfoController.UrlWorker(files.get(i)) {
                @Override
                public void cleanup(final DescriptiveUrlBag result, final ch.cyberduck.core.exception.BackgroundException failure) {
                    super.cleanup(result, failure);
                    results[index] = null == result ? DescriptiveUrlBag.empty() : result;
                    if(remaining.decrementAndGet() == 0) {
                        done.accept(Arrays.asList(results));
                    }
                }
            }));
        }
    }

    /**
     * Put the addresses of the selected files, or of the folder that is shown, on the clipboard
     */
    void copyUrl() {
        this.urls(this.selectedOrWorkdir(), bags -> {
            final List<String> lines = new ArrayList<>();
            for(DescriptiveUrlBag bag : bags) {
                if(!bag.isEmpty()) {
                    final DescriptiveUrl preferred = bag.find(DescriptiveUrl.Type.provider);
                    lines.add((preferred != DescriptiveUrl.EMPTY ? preferred : bag.iterator().next()).getUrl());
                }
            }
            if(!lines.isEmpty()) {
                final ClipboardContent content = new ClipboardContent();
                content.putString(String.join(System.lineSeparator(), lines));
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                this.message(String.format(Messages.get("Copied {0} URL").replace("{0}", "%d"), lines.size()));
            }
        });
    }

    /**
     * Show the first selected file, or else the folder that is shown, in the web browser, when the server has a web address for it
     */
    void openInWebBrowser() {
        this.urls(this.selectedOrWorkdir().subList(0, Math.min(1, this.selectedOrWorkdir().size())), bags -> {
            final DescriptiveUrl web = bags.isEmpty() ? DescriptiveUrl.EMPTY : bags.get(0).find(DescriptiveUrl.Type.http);
            if(web == DescriptiveUrl.EMPTY) {
                dialogs.error(Messages.get("Open in Web Browser"), Messages.get("The server has no web address for this file."));
            }
            else {
                this.browse(web.getUrl());
            }
        });
    }

    /**
     * Open a terminal with a shell on the server in the folder that is shown or the selected folder
     */
    void openTerminal() {
        if(!this.isMounted() || null == workdir || !TerminalLauncher.isSupported(pool.getHost())) {
            return;
        }
        final Path selected = table.getSelectionModel().getSelectedItem();
        final Path folder = null != selected && selected.isDirectory() ? selected : workdir;
        final String failure = TerminalLauncher.open(pool.getHost(), folder);
        if(failure != null) {
            dialogs.error(Messages.get("Open in Terminal"), failure);
        }
    }

    /**
     * Files copied or cut, to be pasted in a folder of the same connection, also in another window
     */
    private static final class PathClipboard {
        private final List<Path> files;
        private final Host host;
        private final boolean cut;

        private PathClipboard(final List<Path> files, final Host host, final boolean cut) {
            this.files = files;
            this.host = host;
            this.cut = cut;
        }
    }

    private static volatile PathClipboard clipboard;

    void copyFiles() {
        this.remember(false);
    }

    void cutFiles() {
        this.remember(true);
    }

    private void remember(final boolean cut) {
        if(this.isMounted() && !table.getSelectionModel().isEmpty()) {
            clipboard = new PathClipboard(new ArrayList<>(table.getSelectionModel().getSelectedItems()), pool.getHost(), cut);
            this.message(String.format(Messages.get(cut ? "Cut {0} items" : "Copied {0} items").replace("{0}", "%d"), clipboard.files.size()));
        }
    }

    /**
     * Put what was copied or cut in the selected folder or else the folder that is shown
     */
    void paste() {
        final PathClipboard remembered = clipboard;
        if(null == remembered || !this.isMounted() || null == workdir) {
            return;
        }
        if(!remembered.host.equals(pool.getHost())) {
            dialogs.error(Messages.get("Paste"), Messages.get("Files can only be pasted in the connection they were copied from."));
            return;
        }
        final Path selected = table.getSelectionModel().getSelectedItem();
        final Path folder = table.getSelectionModel().getSelectedItems().size() == 1 && selected.isDirectory() ? selected : workdir;
        final Map<Path, Path> files = new java.util.LinkedHashMap<>();
        final java.util.Set<String> taken = new java.util.HashSet<>();
        for(Path file : remembered.files) {
            if(folder.equals(file) || folder.isChild(file)) {
                // A folder cannot go into itself
                continue;
            }
            if(remembered.cut && folder.equals(file.getParent())) {
                // Nothing to move
                continue;
            }
            String name = file.getName();
            if(!remembered.cut) {
                // A copy next to the original, or over an existing file, gets a name of its own
                final String base = FilenameUtils.getBaseName(name);
                final String extension = FilenameUtils.getExtension(name);
                int count = 0;
                while(cache.get(folder).contains(new Path(folder, name, file.getType())) || !taken.add(name)) {
                    name = String.format("%s %s%s%s", base, Messages.get("copy"), count++ == 0 ? "" : " " + count, StringUtils.isEmpty(extension) ? "" : "." + extension);
                    if(count > 1000) {
                        break;
                    }
                }
            }
            files.put(file, new Path(folder, name, file.getType()));
        }
        if(files.isEmpty()) {
            return;
        }
        final Path select = files.values().iterator().next();
        if(remembered.cut) {
            clipboard = null;
            this.move(files, select);
        }
        else {
            this.copy(files, select);
        }
    }

    /**
     * Move files into a folder of the same connection, for example when they are dragged onto the folder
     */
    void moveTo(final List<Path> files, final Path folder) {
        final Map<Path, Path> moves = new java.util.LinkedHashMap<>();
        for(Path file : files) {
            if(folder.equals(file) || folder.isChild(file) || folder.equals(file.getParent())) {
                continue;
            }
            moves.put(file, new Path(folder, file.getName(), file.getType()));
        }
        if(!moves.isEmpty() && this.isMounted()) {
            this.move(moves, null);
        }
    }

    /**
     * Ask for a new name for the selected file
     */
    void rename() {
        if(!this.isMounted() || null == workdir) {
            return;
        }
        final List<Path> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if(selected.size() != 1) {
            return;
        }
        final Path file = selected.get(0);
        final String name = dialogs.input("Rename", String.format("Enter the new name for %s", file.getName()), file.getName());
        if(StringUtils.isBlank(name) || StringUtils.trim(name).equals(file.getName())) {
            return;
        }
        final Path renamed = new Path(file.getParent(), StringUtils.trim(name), file.getType());
        this.move(Collections.singletonMap(file, renamed), renamed);
    }

    /**
     * Delete the selected files after confirmation
     */
    void delete() {
        if(!this.isMounted() || null == workdir) {
            return;
        }
        final List<Path> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if(selected.isEmpty()) {
            return;
        }
        final String names = selected.stream().limit(5).map(Path::getName).collect(Collectors.joining(", "));
        if(!dialogs.confirm(selected.size() == 1 ? String.format("Delete %s", selected.get(0).getName()) : String.format("Delete %d items", selected.size()),
            String.format("The following will be deleted from the server and cannot be restored: %s%s", names, selected.size() > 5 ? ", …" : StringUtils.EMPTY),
            "Delete", "Cancel", false).accepted()) {
            return;
        }
        this.background(new WorkerBackgroundAction<>(this, pool, new DeleteWorker(LoginCallbackFactory.get(this), selected, this) {
            @Override
            public void cleanup(final List<Path> deleted) {
                super.cleanup(deleted);
                for(Path file : selected) {
                    if(file.isDirectory()) {
                        cache.invalidate(file);
                    }
                }
                reload();
            }
        }));
    }

    Button getNewFolderButton() {
        return newFolder;
    }

    Button getRenameButton() {
        return rename;
    }

    Button getDeleteButton() {
        return delete;
    }

    /**
     * Choose files on this computer and upload them to the folder that is shown
     */
    void upload() {
        if(!this.isMounted() || null == workdir) {
            return;
        }
        final FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("Upload"));
        final Local suggested = new UploadDirectoryFinder().find(pool.getHost());
        if(suggested.exists()) {
            chooser.setInitialDirectory(new File(suggested.getAbsolute()));
        }
        final List<File> files = chooser.showOpenMultipleDialog(stage);
        if(files != null) {
            this.upload(files);
        }
    }

    /**
     * Upload files to the selected folder or else the folder that is shown
     */
    void upload(final List<File> files) {
        this.upload(files, null);
    }

    /**
     * @param folder Where to put the files or null for the selected folder or else the folder that is shown
     */
    void upload(final List<File> files, final Path folder) {
        if(!this.isMounted() || null == workdir || files.isEmpty()) {
            return;
        }
        final Host host = pool.getHost();
        final Path destination = null != folder ? folder : new UploadTargetFinder(workdir).find(table.getSelectionModel().getSelectedItem());
        final List<TransferItem> uploads = new ArrayList<>();
        for(File file : files) {
            final Local local = LocalFactory.get(file.getAbsolutePath());
            uploads.add(new TransferItem(new Path(destination, local.getName(),
                local.isDirectory() ? EnumSet.of(Path.Type.directory) : EnumSet.of(Path.Type.file)), local));
        }
        log.debug("Upload {} to {}", uploads, destination);
        this.transfer(new UploadTransfer(host, uploads));
    }

    /**
     * Start dragging the selected files and folders to another application, such as the file manager. They are
     * downloaded to a place of their own and become available when they are complete.
     */
    private void dragOut(final MouseEvent event, final TableRow<Path> row) {
        if(!this.isMounted() || row.isEmpty()) {
            return;
        }
        if(!row.isSelected()) {
            table.getSelectionModel().clearAndSelect(row.getIndex());
        }
        final List<Path> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        final DragStaging.Batch batch;
        try {
            batch = DragStaging.create();
        }
        catch(IOException e) {
            log.warn("Failure preparing to drag {}. {}", selected, e.getMessage());
            return;
        }
        final List<TransferItem> items = new ArrayList<>();
        final List<File> files = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for(Path file : selected) {
            items.add(new TransferItem(file, LocalFactory.get(batch.work(file.getName()).toString())));
            files.add(batch.ready(file.getName()).toFile());
            names.add(file.getName());
        }
        final Dragboard board = row.startDragAndDrop(TransferMode.COPY_OR_MOVE);
        final ClipboardContent content = new ClipboardContent();
        content.putFiles(files);
        board.setContent(content);
        log.debug("Drag {} out as {}", selected, files);
        // The download starts when the files leave the window. Dropped on a folder of this window they are moved instead.
        dragged = selected;
        pendingDrag = () -> TransferController.get().start(new DownloadTransfer(pool.getHost(), items), new TransferOptions(), this, completed -> {
            if(completed.isComplete()) {
                batch.publish(names);
            }
        });
        event.consume();
    }

    /**
     * Files of this listing that are being dragged and have not left the window yet
     */
    private volatile Runnable pendingDrag;
    private volatile List<Path> dragged;

    private void startPendingDrag() {
        final Runnable start = pendingDrag;
        pendingDrag = null;
        if(start != null) {
            start.run();
        }
    }

    /**
     * @return True when the files are dragged from the listing of this window
     */
    private boolean isInternal(final DragEvent event) {
        return null != dragged && event.getGestureSource() instanceof javafx.scene.Node source && source.getScene() == table.getScene();
    }

    private void acceptDrag(final DragEvent event, final Path folder) {
        if(this.isInternal(event)) {
            // Files of the listing can be moved into another folder of the listing
            if(folder != null && !dragged.contains(folder)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
        }
        else if(this.isMounted() && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    /**
     * Upload the files that were dropped from the desktop
     *
     * @param folder Folder that the files were dropped on or null for the folder that is shown
     */
    private void drop(final DragEvent event, final Path folder) {
        boolean completed = false;
        if(this.isInternal(event)) {
            if(folder != null) {
                final List<Path> files = dragged;
                pendingDrag = null;
                this.moveTo(files, folder);
                completed = true;
            }
        }
        else if(this.isMounted() && event.getDragboard().hasFiles()) {
            this.upload(event.getDragboard().getFiles(), folder);
            completed = true;
        }
        event.setDropCompleted(completed);
        event.consume();
    }

    private void transfer(final Transfer transfer) {
        TransferController.get().start(transfer, new TransferOptions(), this, completed -> {
            this.message(String.format("%s completed", completed.getName()));
            // Show the new files
            this.invoke(new DefaultMainAction() {
                @Override
                public void run() {
                    reload();
                }
            });
        });
    }

    Button getUploadButton() {
        return upload;
    }

    Button getDownloadButton() {
        return download;
    }

    Button getBackButton() {
        return back;
    }

    Button getUpButton() {
        return up;
    }

    String getStatusText() {
        return status.getText();
    }

    TextField getLocation() {
        return location;
    }

    /**
     * Discard cached listing and list again
     */
    public void reload() {
        if(null != workdir) {
            cache.invalidate(workdir);
            this.navigate(workdir, false);
        }
    }

    private void render(final Path directory) {
        final boolean showHidden = preferences.getBoolean("browser.showHidden");
        final AttributedList<Path> list = cache.get(directory).filter(DEFAULT_ORDER,
            showHidden ? new NullFilter<>() : new DefaultBrowserFilter());
        if(!directory.equals(rendered)) {
            search.clear();
        }
        rows.setAll(list.toList());
        rendered = directory;
        renderedProperty.set(directory);
        if(selectAfterRender != null) {
            final Path select = selectAfterRender;
            selectAfterRender = null;
            rows.stream().filter(p -> p.equals(select)).findFirst().ifPresent(p -> {
                table.getSelectionModel().clearSelection();
                table.getSelectionModel().select(p);
                table.scrollTo(p);
            });
        }
        this.summarize();
    }
}
