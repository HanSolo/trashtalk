package eu.hansolo.trashtalk.copying;

import eu.hansolo.toolbox.observables.ObservableMatrix;
import eu.hansolo.trashtalk.Fonts;
import eu.hansolo.trashtalk.CellState;
import eu.hansolo.trashtalk.GCState;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;


public class Copying extends Application {
    private ObservableMatrix<Obj> survivor1;
    private ObservableMatrix<Obj> survivor2;
    private GridPane              survivor1Grid;
    private GridPane              survivor2Grid;
    private Label                 survivor1Label;
    private Label                 survivor2Label;
    private Label                 stateLabel;
    private ImageView             stopImage;
    private GridPane              grid;
    private Allocator      allocator;
    private Collector      collector;
    private AnimationTimer mutatorThread;
    private long                  lastMutatorCall;
    private long                  lastAgingCall;
    private long                  lastGarbageCollector;



    @Override public void init() {
        this.survivor1 = new ObservableMatrix<>(Obj.class, Constants.NO_OF_ROWS_SURVIVER, Constants.NO_OF_COLS_SURVIVER);
        this.survivor2 = new ObservableMatrix<>(Obj.class, Constants.NO_OF_ROWS_SURVIVER, Constants.NO_OF_COLS_SURVIVER);
        for (int y = 0; y < Constants.NO_OF_COLS_SURVIVER; y++) {
            for (int x = 0; x < Constants.NO_OF_ROWS_SURVIVER; x++) {
                this.survivor1.setItemAt(x, y, new Obj());
                this.survivor2.setItemAt(x, y, new Obj());
            }
        }

        this.survivor1Grid = new GridPane();
        this.survivor1Grid.setHgap(5);
        this.survivor1Grid.setVgap(5);
        this.survivor1Grid.setBackground(new Background(new BackgroundFill(Constants.TO_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
        for (int y = 0; y < Constants.NO_OF_COLS_SURVIVER; y++) {
            for (int x = 0; x < Constants.NO_OF_ROWS_SURVIVER; x++) {
                Obj obj = this.survivor1.getItemAt(x, y);
                this.survivor1Grid.add(new ObjRegion(obj), y, x);
            }
        }

        this.survivor2Grid = new GridPane();
        this.survivor2Grid.setHgap(5);
        this.survivor2Grid.setVgap(5);
        this.survivor2Grid.setBackground(new Background(new BackgroundFill(Constants.FROM_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
        for (int y = 0; y < Constants.NO_OF_COLS_SURVIVER; y++) {
            for (int x = 0; x < Constants.NO_OF_ROWS_SURVIVER; x++) {
                Obj obj = this.survivor2.getItemAt(x, y);
                this.survivor2Grid.add(new ObjRegion(obj), y, x);
            }
        }

        survivor1Grid.setPadding(new Insets(10));
        survivor2Grid.setPadding(new Insets(10));

        this.grid = new GridPane();

        survivor1Label = new Label("To-Space");
        survivor1Label.setAlignment(Pos.CENTER);
        survivor1Label.setTextFill(Color.WHITE);
        survivor1Label.setFont(Fonts.avenirNextLtProRegular(16));

        survivor2Label = new Label("From-Space");
        survivor2Label.setAlignment(Pos.CENTER);
        survivor2Label.setTextFill(Color.WHITE);
        survivor2Label.setFont(Fonts.avenirNextLtProRegular(16));

        this.grid.add(survivor1Grid, 0, 0);
        this.grid.add(survivor2Grid, 1, 0);
        this.grid.add(new StackPane(survivor1Label), 0, 1);
        this.grid.add(new StackPane(survivor2Label), 1, 1);


       Label heapLabel = new Label("Heap");
        heapLabel.setAlignment(Pos.CENTER);
        heapLabel.setTextFill(Color.WHITE);
        heapLabel.setFont(Fonts.avenirNextLtProRegular(16));
        StackPane heapPane = new StackPane(heapLabel);

        stateLabel = new Label("");
        stateLabel.setAlignment(Pos.CENTER);
        stateLabel.setTextFill(Color.WHITE);
        stateLabel.setFont(Fonts.avenirNextLtProRegular(16));
        StackPane statePane = new StackPane(stateLabel);
        statePane.setAlignment(Pos.CENTER_LEFT);
        GridPane.setColumnSpan(statePane, 4);

        stopImage = new ImageView(new Image(Fonts.class.getResourceAsStream("/eu/hansolo/trashtalk/stop.png")));
        stopImage.setFitWidth(32);
        stopImage.setFitHeight(32);
        stopImage.setVisible(false);
        StackPane stopPane = new StackPane(stopImage);
        stopPane.setAlignment(Pos.CENTER_RIGHT);
        GridPane.setColumnSpan(stopPane, 4);

        grid.add(statePane, 0, 2);
        //grid.add(heapPane, 6, 2);
        grid.add(stopPane, 1, 2);

        this.allocator = new Allocator();
        this.collector = new Collector();

        this.lastMutatorCall      = System.nanoTime();
        this.lastAgingCall        = System.nanoTime();
        this.lastGarbageCollector = System.nanoTime();

        mutatorThread = new AnimationTimer() {
            @Override public void handle(final long now) {
                if (now > lastMutatorCall + Constants.ALLOCATION_INTERVAL) {

                    if (collector.toggleSurvivorProperty().get()) {
                        // Allocate memory and if no memory available run a garbage collection
                        int amountToAllocate = Constants.SIZES[Constants.RND.nextInt(0, Constants.SIZES.length)];
                        if (!collector.isCollecting() && !allocator.allocate(survivor2, amountToAllocate)) {
                            System.out.println("Garbage Collection (after " + ((System.nanoTime() - lastGarbageCollector) / 1_000_000) + " ms)");
                            collector.collect(List.of(allocator), survivor1, survivor2, allocator, amountToAllocate, mutatorThread);
                            lastGarbageCollector = System.nanoTime();
                        }
                    } else {
                        // Allocate memory and if no memory available run a garbage collection
                        int amountToAllocate = Constants.SIZES[Constants.RND.nextInt(0, Constants.SIZES.length)];
                        if (!collector.isCollecting() && !allocator.allocate(survivor1, amountToAllocate)) {
                            System.out.println("Garbage Collection (after " + ((System.nanoTime() - lastGarbageCollector) / 1_000_000) + " ms)");
                            collector.collect(List.of(allocator), survivor1, survivor2, allocator, amountToAllocate, mutatorThread);
                            lastGarbageCollector = System.nanoTime();
                        }
                    }

                    lastMutatorCall = now;
                }

                // Call all objects to reduce their lifespan
                if (!collector.isCollecting() && now > lastAgingCall + Constants.AGING_INTERVAL) {
                    survivor1.stream().filter(ref -> CellState.REFERENCED == ref.get().getState()).forEach(ref -> ref.get().decLifespan());
                    survivor2.stream().filter(ref -> CellState.REFERENCED == ref.get().getState()).forEach(ref -> ref.get().decLifespan());
                    lastAgingCall = now;
                }
            }
        };


        registerListeners();
    }

    private void registerListeners() {
        this.collector.toggleSurvivorProperty().addListener((o, ov, nv) -> {
            if (nv) {
                // Survivor 2 -> TO-SPACE
                survivor2Grid.setBackground(new Background(new BackgroundFill(Constants.TO_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
                survivor1Grid.setBackground(new Background(new BackgroundFill(Constants.FROM_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
                survivor2Label.setText("To-Space");
                survivor1Label.setText("From-Space");
            } else {
                // Survivor 1 -> TO-SPACE
                survivor1Grid.setBackground(new Background(new BackgroundFill(Constants.TO_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
                survivor2Grid.setBackground(new Background(new BackgroundFill(Constants.FROM_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
                survivor2Label.setText("From-Space");
                survivor1Label.setText("To-Space");
            }
        });
        this.collector.stateProperty().addListener((o, ov, nv) -> {
            this.stateLabel.setText(nv.getName());
        });
        this.collector.collectingProperty().addListener((o, ov, nv) -> {
            this.stopImage.setVisible(nv);
        });
    }

    @Override public void start(final Stage stage) {
        StackPane pane = new StackPane(grid);
        pane.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));
        pane.setPadding(new Insets(20, 20, 20, 30));

        Scene scene = new Scene(pane, 650, 700, Constants.WINDOW_BACKGROUND);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, evt -> {
            if (KeyCode.SPACE.equals(evt.getCode())) {
                this.collector.setState(GCState.ALLOCATING);
                mutatorThread.start();
            }
        });

        stage.setTitle("Copying");
        stage.setScene(scene);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.show();
        stage.centerOnScreen();
    }

    @Override public void stop() {
        Platform.exit();
    }

    public static void main(final String[] args) {
        launch(args);
    }
}
