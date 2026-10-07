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
import ch.cyberduck.core.NullFilter;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathCache;
import ch.cyberduck.core.Permission;
import ch.cyberduck.core.SessionPoolFactory;
import ch.cyberduck.core.UserDateFormatterFactory;
import ch.cyberduck.core.formatter.SizeFormatterFactory;
import ch.cyberduck.core.pool.SessionPool;
import ch.cyberduck.core.preferences.Preferences;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.threading.DisconnectBackgroundAction;
import ch.cyberduck.core.threading.WorkerBackgroundAction;
import ch.cyberduck.core.worker.ListWorker;
import ch.cyberduck.core.worker.MountWorker;
import ch.cyberduck.ui.browser.DefaultBrowserFilter;
import ch.cyberduck.ui.comparator.FilenameComparator;
import ch.cyberduck.ui.comparator.OwnerComparator;
import ch.cyberduck.ui.comparator.PermissionsComparator;
import ch.cyberduck.ui.comparator.SizeComparator;
import ch.cyberduck.ui.comparator.TimestampComparator;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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

    public BrowserController(final Stage stage) {
        this.stage = stage;
        this.stage.setTitle(preferences.getProperty("application.name"));
        this.stage.setScene(new Scene(this.build(), 900, 600));
    }

    private BorderPane build() {
        location.setEditable(false);
        HBox.setHgrow(location, Priority.ALWAYS);
        final HBox top = new HBox(8, location);
        top.setPadding(new Insets(8));
        top.setAlignment(Pos.CENTER_LEFT);

        table.setItems(rows);
        table.setPlaceholder(new Label());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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

        final BorderPane root = new BorderPane(table);
        root.setTop(top);
        root.setBottom(bottom);
        return root;
    }

    private TableColumn<Path, Path> column(final String title, final double width, final Comparator<Path> comparator,
                                           final java.util.function.Function<Path, String> text, final Pos alignment) {
        final TableColumn<Path, Path> column = new TableColumn<>(title);
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

    public void show() {
        stage.show();
    }

    public Stage getStage() {
        return stage;
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
                rendered = null;
                rows.clear();
                summary.set(StringUtils.EMPTY);
                location.clear();
                stage.setTitle(preferences.getProperty("application.name"));
                disconnected.run();
            }
        });
    }

    /**
     * Show the contents of a directory. Lists the directory from the server if it is not in the cache.
     *
     * @param directory Folder to display
     */
    public void setWorkdir(final Path directory) {
        log.debug("Set working directory to {}", directory);
        workdir = directory;
        location.setText(directory.getAbsolute());
        if(cache.isValid(directory)) {
            this.render(directory);
            return;
        }
        this.background(new WorkerBackgroundAction<>(this, pool, new ListWorker(cache, directory, listener) {
            @Override
            public void cleanup(final AttributedList<Path> list) {
                super.cleanup(list);
                if(directory.equals(workdir)) {
                    render(directory);
                }
            }
        }));
    }

    /**
     * Discard cached listing and list again
     */
    public void reload() {
        if(null != workdir) {
            cache.invalidate(workdir);
            this.setWorkdir(workdir);
        }
    }

    private void render(final Path directory) {
        final boolean showHidden = preferences.getBoolean("browser.showHidden");
        final AttributedList<Path> list = cache.get(directory).filter(DEFAULT_ORDER,
            showHidden ? new NullFilter<>() : new DefaultBrowserFilter());
        rows.setAll(list.toList());
        rendered = directory;
        summary.set(String.format("%d items", rows.size()));
    }
}
