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

import ch.cyberduck.core.CollectionListener;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.ProgressListener;
import ch.cyberduck.core.SessionPoolFactory;
import ch.cyberduck.core.TransferCollection;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.formatter.SizeFormatterFactory;
import ch.cyberduck.core.local.RevealServiceFactory;
import ch.cyberduck.core.pool.SessionPool;
import ch.cyberduck.core.threading.BackgroundAction;
import ch.cyberduck.core.threading.TransferBackgroundAction;
import ch.cyberduck.core.threading.TransferCollectionBackgroundAction;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.TransferCallback;
import ch.cyberduck.core.transfer.TransferItem;
import ch.cyberduck.core.transfer.TransferListener;
import ch.cyberduck.core.transfer.TransferOptions;
import ch.cyberduck.core.transfer.TransferProgress;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Runs transfers on their own connections and shows them in a window. One for the whole application.
 */
public class TransferController extends FxController implements TransferListener {
    private static final Logger log = LogManager.getLogger(TransferController.class);

    private static TransferController instance;

    public static synchronized TransferController get() {
        if(null == instance) {
            instance = new TransferController();
        }
        return instance;
    }

    private final TransferCollection collection = TransferCollection.defaultCollection();

    private final AtomicInteger started = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger progress = new AtomicInteger();

    /**
     * What the table shows about a transfer that is not in the transfer itself
     */
    static final class State {
        final StringProperty detail = new SimpleStringProperty(StringUtils.EMPTY);
        final DoubleProperty fraction = new SimpleDoubleProperty(0d);
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final ObservableList<Transfer> items = FXCollections.observableArrayList();
    private final TableView<Transfer> table = new TableView<>(items);
    private Stage stage;

    /**
     * Run the transfer in the background and add it to the list of transfers
     *
     * @param transfer Transfer to start
     * @param options  Resume or reload
     * @param listener Receives status messages
     * @param callback Called after the transfer has completed successfully
     */
    public void start(final Transfer transfer, final TransferOptions options, final ProgressListener listener, final TransferCallback callback) {
        final Host source = transfer.getSource();
        final Host destination = transfer.getDestination();
        final TransferBackgroundAction action = new TransferCollectionBackgroundAction(this,
            null == source ? SessionPool.DISCONNECTED : SessionPoolFactory.create(this, source, listener),
            null == destination ? SessionPool.DISCONNECTED : SessionPoolFactory.create(this, destination, listener),
            this, listener, transfer, options) {
            @Override
            public void finish() {
                super.finish();
                if(transfer.isComplete()) {
                    completed.incrementAndGet();
                    callback.complete(transfer);
                }
            }

            @Override
            public void cleanup(final Boolean result, final BackgroundException failure) {
                super.cleanup(result, failure);
                if(failure != null) {
                    log.warn("Transfer {} failed. {}", transfer, failure.getMessage());
                }
            }
        };
        if(!collection.contains(transfer)) {
            collection.add(transfer);
        }
        this.background(action);
    }

    /**
     * @return Number of transfers that finished without failure since the application started
     */
    public int getCompleted() {
        return completed.get();
    }

    /**
     * @return Number of progress notifications received since the application started
     */
    public int getProgressEvents() {
        return progress.get();
    }

    @Override
    public void transferDidStart(final Transfer transfer) {
        log.debug("Transfer {} did start", transfer);
        started.incrementAndGet();
        this.update(transfer, null);
    }

    @Override
    public void transferDidStop(final Transfer transfer) {
        log.debug("Transfer {} did stop", transfer);
        this.update(transfer, null);
    }

    @Override
    public void transferDidProgress(final Transfer transfer, final TransferProgress status) {
        progress.incrementAndGet();
        this.update(transfer, status);
    }

    private State state(final Transfer transfer) {
        return states.computeIfAbsent(transfer.getUuid(), uuid -> new State());
    }

    /**
     * Update what is shown for the transfer. May be called from any thread.
     */
    private void update(final Transfer transfer, final TransferProgress status) {
        final String detail;
        final double fraction;
        if(status != null && status.getSize() != null && status.getSize() > 0) {
            fraction = Math.min(1d, (double) status.getTransferred() / status.getSize());
            detail = status.getProgress();
        }
        else {
            fraction = transfer.isComplete() ? 1d : -1d;
            detail = null;
        }
        Platform.runLater(() -> {
            final State state = this.state(transfer);
            if(detail != null) {
                state.detail.set(detail);
            }
            if(!transfer.isRunning() && transfer.isComplete()) {
                state.fraction.set(1d);
            }
            else if(fraction >= 0d) {
                state.fraction.set(fraction);
            }
            else if(transfer.isRunning()) {
                state.fraction.set(ProgressBar.INDETERMINATE_PROGRESS);
            }
            table.refresh();
        });
    }

    /**
     * @return Fraction between 0 and 1 shown in the progress column
     */
    double fraction(final Transfer transfer) {
        return this.state(transfer).fraction.get();
    }

    /**
     * @return Text for the status column
     */
    String status(final Transfer transfer) {
        if(transfer.isRunning()) {
            final String detail = this.state(transfer).detail.get();
            return StringUtils.isBlank(detail) ? "Running" : detail;
        }
        return transfer.isComplete() ? "Complete" : "Incomplete";
    }

    /**
     * Show the window with the transfers. Call on the JavaFX application thread.
     */
    public void show() {
        if(null == stage) {
            stage = new Stage();
            stage.setTitle("Transfers");
            stage.setScene(new Scene(this.build(), 720, 360));
            collection.addListener(new CollectionListener<Transfer>() {
                @Override
                public void collectionLoaded() {
                    refresh();
                }

                @Override
                public void collectionItemAdded(final Transfer item) {
                    refresh();
                }

                @Override
                public void collectionItemRemoved(final Transfer item) {
                    refresh();
                }

                @Override
                public void collectionItemChanged(final Transfer item) {
                    refresh();
                }
            });
            this.refresh();
        }
        stage.show();
        stage.toFront();
    }

    Stage getStage() {
        return stage;
    }

    TableView<Transfer> getTable() {
        return table;
    }

    private void refresh() {
        final Runnable action = () -> {
            items.setAll(collection);
            table.refresh();
        };
        if(Platform.isFxApplicationThread()) {
            action.run();
        }
        else {
            Platform.runLater(action);
        }
    }

    private BorderPane build() {
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No transfers"));

        final TableColumn<Transfer, Transfer> name = new TableColumn<>("Name");
        name.setPrefWidth(260);
        name.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        name.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(final Transfer item, final boolean empty) {
                super.updateItem(item, empty);
                setText(empty || null == item ? null : String.format("%s %s", StringUtils.capitalize(item.getType().name()), item.getName()));
            }
        });
        final TableColumn<Transfer, Transfer> status = new TableColumn<>("Status");
        status.setPrefWidth(260);
        status.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        status.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(final Transfer item, final boolean empty) {
                super.updateItem(item, empty);
                setText(empty || null == item ? null : status(item));
            }
        });
        final TableColumn<Transfer, Transfer> bar = new TableColumn<>("Progress");
        bar.setPrefWidth(160);
        bar.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        bar.setCellFactory(column -> new TableCell<>() {
            private final ProgressBar indicator = new ProgressBar(0);

            {
                indicator.setMaxWidth(Double.MAX_VALUE);
            }

            @Override
            protected void updateItem(final Transfer item, final boolean empty) {
                super.updateItem(item, empty);
                indicator.progressProperty().unbind();
                if(empty || null == item) {
                    setGraphic(null);
                    return;
                }
                indicator.progressProperty().bind(state(item).fraction);
                setGraphic(indicator);
            }
        });
        table.getColumns().addAll(name, status, bar);

        final Button stop = new Button("Stop");
        stop.setOnAction(event -> this.stop());
        final Button resume = new Button("Resume");
        resume.setOnAction(event -> this.resume());
        final Button remove = new Button("Remove");
        remove.setOnAction(event -> this.remove());
        final Button clear = new Button("Clear");
        clear.setOnAction(event -> this.clear());
        final Button reveal = new Button("Open Folder");
        reveal.setOnAction(event -> this.reveal());
        final var selected = table.getSelectionModel().selectedItemProperty().isNull();
        stop.disableProperty().bind(selected);
        resume.disableProperty().bind(selected);
        remove.disableProperty().bind(selected);
        reveal.disableProperty().bind(selected);
        final HBox buttons = new HBox(8, stop, resume, remove, clear, reveal);
        buttons.setPadding(new Insets(8));

        final BorderPane root = new BorderPane(table);
        root.setTop(buttons);
        return root;
    }

    /**
     * Cancel the running transfers that are selected
     */
    void stop() {
        for(Transfer transfer : new ArrayList<>(table.getSelectionModel().getSelectedItems())) {
            if(transfer.isRunning()) {
                for(BackgroundAction<?> action : registry.toArray(new BackgroundAction[registry.size()])) {
                    if(action instanceof TransferBackgroundAction t && t.getTransfer().equals(transfer)) {
                        log.debug("Cancel {}", action);
                        t.cancel();
                    }
                }
            }
        }
    }

    /**
     * Continue the transfers that are selected and not running
     */
    void resume() {
        for(Transfer transfer : new ArrayList<>(table.getSelectionModel().getSelectedItems())) {
            if(!transfer.isRunning()) {
                this.start(transfer, new TransferOptions().resume(true).reload(false), this, t -> {
                    //
                });
            }
        }
    }

    /**
     * Remove the selected transfers that are not running from the list. Files are not touched.
     */
    void remove() {
        final List<Transfer> remove = new ArrayList<>();
        for(Transfer transfer : table.getSelectionModel().getSelectedItems()) {
            if(!transfer.isRunning()) {
                remove.add(transfer);
            }
        }
        collection.removeAll(remove);
        collection.save();
    }

    /**
     * Remove all completed transfers from the list
     */
    void clear() {
        collection.removeIf(Transfer::isComplete);
        collection.save();
    }

    /**
     * Show the files of the selected transfers in the file manager
     */
    void reveal() {
        for(Transfer transfer : new ArrayList<>(table.getSelectionModel().getSelectedItems())) {
            for(TransferItem item : transfer.getRoots()) {
                RevealServiceFactory.get().reveal(item.local);
            }
        }
    }

    /**
     * Text of the size, for example in status messages
     */
    static String size(final long bytes) {
        return SizeFormatterFactory.get().format(bytes);
    }
}
