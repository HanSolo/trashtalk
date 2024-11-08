package eu.hansolo.trashtalk.markandsweep;

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

import static eu.hansolo.trashtalk.markandsweep.Constants.AGING_INTERVAL;
import static eu.hansolo.trashtalk.markandsweep.Constants.ALLOCATION_INTERVAL;
import static eu.hansolo.trashtalk.markandsweep.Constants.NO_OF_COLS;
import static eu.hansolo.trashtalk.markandsweep.Constants.NO_OF_ROWS;
import static eu.hansolo.trashtalk.markandsweep.Constants.RND;
import static eu.hansolo.trashtalk.markandsweep.Constants.SIZES;


public class MarkAndSweep extends Application {
    private ObservableMatrix<Obj> heap;
    private Label                 stateLabel;
    private ImageView             stopImage;
    private GridPane              grid;
    private Allocator             allocator;
    private Collector             collector;
    private AnimationTimer        mutatorThread;
    private long                  lastMutatorCall;
    private long                  lastAgingCall;
    private long                  lastGarbageCollector;



    @Override public void init() {
        this.heap = new ObservableMatrix<>(Obj.class, NO_OF_COLS, NO_OF_ROWS);
        for (int y = 0 ; y < NO_OF_ROWS  ; y++) {
            for (int x = 0 ; x < NO_OF_COLS ; x++) {
                this.heap.setItemAt(x, y, new Obj());
            }
        }

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
        stopPane.setPadding(new Insets(0, 16, 0, 0));
        stopPane.setAlignment(Pos.CENTER_RIGHT);
        GridPane.setColumnSpan(stopPane, 4);


        this.grid = new GridPane();
        this.grid.setHgap(5);
        this.grid.setVgap(5);
        this.grid.setPadding(new Insets(10));
        for (int y = 0 ; y < NO_OF_ROWS  ; y++) {
            for (int x = 0 ; x < NO_OF_COLS ; x++) {
                Obj obj = this.heap.getItemAt(x, y);
                this.grid.add(new ObjRegion(obj), y, x);
            }
        }

        grid.add(statePane, 0, NO_OF_ROWS);
        grid.add(heapPane, 6, NO_OF_ROWS);
        grid.add(stopPane, 13, NO_OF_ROWS);

        this.allocator = new Allocator();
        this.collector = new Collector();

        this.lastMutatorCall      = System.nanoTime();
        this.lastAgingCall        = System.nanoTime();
        this.lastGarbageCollector = System.nanoTime();

        mutatorThread = new AnimationTimer() {
            @Override public void handle(final long now) {
                if (now > lastMutatorCall + ALLOCATION_INTERVAL) {

                    // Allocate memory and if no memory available run a garbage collection
                    int amountToAllocate = SIZES[RND.nextInt(0, SIZES.length)];
                    if (!collector.isCollecting() && !allocator.allocate(heap, amountToAllocate)) {
                        System.out.println("Garbage Collection (after " + ((System.nanoTime() - lastGarbageCollector) / 1_000_000) + " ms)");
                        collector.collect(List.of(allocator), heap, allocator, amountToAllocate, mutatorThread);
                        lastGarbageCollector = System.nanoTime();
                    }
                    lastMutatorCall = now;
                }

                // Call all objects to reduce their lifespan
                if (!collector.isCollecting() && now > lastAgingCall + AGING_INTERVAL) {
                    heap.stream().filter(ref -> CellState.REFERENCED == ref.get().getState()).forEach(ref -> ref.get().decLifespan());
                    lastAgingCall = now;
                }
            }
        };

        registerListeners();
    }

    private void registerListeners() {
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
        pane.setPadding(new Insets(20, 20, 20, 35));

        Scene scene = new Scene(pane, 650, 700, Constants.WINDOW_BACKGROUND);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, evt -> {
            if (KeyCode.SPACE.equals(evt.getCode())) {
                this.collector.setState(GCState.ALLOCATING);
                mutatorThread.start();
            }
        });

        stage.setTitle("Mark and Sweep");
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