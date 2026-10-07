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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
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
    private final Button back = new Button(Messages.get("Back"));
    private final Button up = new Button(Messages.get("Up"));
    private final Button refresh = new Button(Messages.get("Refresh"));
    private final Button download = new Button(Messages.get("Download"));
    private final Button upload = new Button(Messages.get("Upload"));
    private final Button transfers = new Button(Messages.get("Transfers"));
    private final Button newFolder = new Button(Messages.get("New Folder"));
    private final Button rename = new Button(Messages.get("Rename"));
    private final Button delete = new Button(Messages.get("Delete"));
    private final StringProperty summary = new SimpleStringProperty(StringUtils.EMPTY);

    private final Cache<Path> cache = new PathCache(preferences.getInteger("browser.cache.size"));
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

    public BrowserController(final Stage stage) {
        this.stage = stage;
        this.stage.setTitle(preferences.getProperty("application.name"));
        this.stage.setScene(new Scene(this.build(), 900, 600));
    }

    private BorderPane build() {
        final BorderPane root = new BorderPane();
        bookmarks = new BookmarkController(this);
        bookmarksToggle.setSelected(true);
        bookmarksToggle.setOnAction(event -> root.setLeft(bookmarksToggle.isSelected() ? bookmarks.getPane() : null));
        connect.setOnAction(event -> this.connect());
        back.setOnAction(event -> this.back());
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
        final HBox top = new HBox(8, bookmarksToggle, connect, back, up, refresh, download, upload, newFolder, rename, delete, transfers, location);
        top.setPadding(new Insets(8));
        top.setAlignment(Pos.CENTER_LEFT);
        // Never cut the labels of the buttons. The path field gives way instead and the window cannot get narrower than the toolbar.
        for(Region button : new Region[]{bookmarksToggle, connect, back, up, refresh, download, upload, newFolder, rename, delete, transfers}) {
            button.setMinWidth(Region.USE_PREF_SIZE);
        }
        location.setPrefWidth(160);
        location.setMinWidth(80);
        stage.setOnShown(event -> stage.setMinWidth(Math.min(
            top.minWidth(-1) + stage.getWidth() - stage.getScene().getWidth(), Screen.getPrimary().getVisualBounds().getWidth())));

        table.setItems(rows);
        table.setPlaceholder(new Label());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(view -> {
            final TableRow<Path> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if(event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    this.open(row.getItem());
                }
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            if(event.getCode() == KeyCode.ENTER) {
                final Path selected = table.getSelectionModel().getSelectedItem();
                if(selected != null) {
                    this.open(selected);
                }
            }
        });
        table.getColumns().add(this.column("Filename", 360, new FilenameComparator(true), p -> {
            final String name = p.getName();
            return p.isDirectory() ? String.format("%s%s", name, Path.DELIMITER) : name;
        }, Pos.CENTER_LEFT));
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
        root.setBottom(bottom);
        this.updateNavigation();
        return root;
    }

    private TableColumn<Path, Path> column(final String title, final double width, final Comparator<Path> comparator,
                                           final Function<Path, String> text, final Pos alignment) {
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
            }
        });
        return column;
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
        final MenuItem preferencesItem = new MenuItem(Messages.get("Preferences…"));
        preferencesItem.setAccelerator(KeyCombination.keyCombination("Shortcut+,"));
        preferencesItem.setOnAction(event -> PreferencesController.get().show());
        final MenuItem quit = new MenuItem(Messages.get("Quit"));
        quit.setAccelerator(KeyCombination.keyCombination("Shortcut+Q"));
        quit.setOnAction(event -> MainController.get().quit());
        final MenuItem showTransfers = new MenuItem(Messages.get("Transfers"));
        showTransfers.setAccelerator(KeyCombination.keyCombination("Shortcut+T"));
        showTransfers.setOnAction(event -> TransferController.get().show());
        menu = new MenuBar(
            new Menu(Messages.get("File"), null, newBrowser, open, disconnect, new SeparatorMenuItem(), preferencesItem, new SeparatorMenuItem(), closeWindow, quit),
            new Menu(Messages.get("Window"), null, showTransfers));
        return menu;
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
        }
        pending = null;
        workdir = directory;
        location.setText(directory.getAbsolute());
        this.render(directory);
        this.updateNavigation();
    }

    private void updateNavigation() {
        back.setDisable(history.isEmpty());
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
            this.navigate(history.pop(), false);
            this.updateNavigation();
        }
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
        log.debug("Download {} to {}", selected, target);
        this.transfer(new DownloadTransfer(host, selected.stream()
            .map(file -> new TransferItem(file, LocalFactory.get(target, file.getName()))).collect(Collectors.toList())));
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
        // Moving needs a second connection to the same server to borrow while the first is in use
        final SessionPool target = pool.getHost().getProtocol().getStatefulness() == Protocol.Statefulness.stateful
            ? SessionPoolFactory.create(this, pool.getHost()) : pool;
        this.background(new WorkerBackgroundAction<>(this, pool,
            new MoveWorker(Collections.singletonMap(file, renamed), target, cache, this, LoginCallbackFactory.get(this)) {
                @Override
                public void cleanup(final Map<Path, Path> result) {
                    super.cleanup(result);
                    if(target != pool) {
                        target.shutdown();
                    }
                    if(file.isDirectory()) {
                        cache.invalidate(file);
                    }
                    if(result != null && !result.isEmpty()) {
                        selectAfterRender = renamed;
                    }
                    reload();
                }
            }));
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
        if(!this.isMounted() || null == workdir || files.isEmpty()) {
            return;
        }
        final Host host = pool.getHost();
        final Path destination = new UploadTargetFinder(workdir).find(table.getSelectionModel().getSelectedItem());
        final List<TransferItem> uploads = new ArrayList<>();
        for(File file : files) {
            final Local local = LocalFactory.get(file.getAbsolutePath());
            uploads.add(new TransferItem(new Path(destination, local.getName(),
                local.isDirectory() ? EnumSet.of(Path.Type.directory) : EnumSet.of(Path.Type.file)), local));
        }
        log.debug("Upload {} to {}", uploads, destination);
        this.transfer(new UploadTransfer(host, uploads));
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
        summary.set(String.format("%d items", rows.size()));
    }
}
