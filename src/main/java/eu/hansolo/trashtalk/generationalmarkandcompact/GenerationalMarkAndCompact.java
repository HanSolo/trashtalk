package eu.hansolo.trashtalk.generationalmarkandcompact;

import eu.hansolo.toolbox.statemachine.StateChangeException;
import eu.hansolo.trashtalk.Fonts;
import eu.hansolo.trashtalk.GCState;
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


public class GenerationalMarkAndCompact extends Application {
    private GridPane  edenGrid;
    private GridPane  survivor1Grid;
    private GridPane  survivor2Grid;
    private GridPane  tenuredGrid;
    private Label     stateLabel;
    private ImageView stopImage;
    private GridPane  grid;
    private Collector collector;



    @Override public void init() throws StateChangeException  {
        this.collector = new Collector();

        this.edenGrid = new GridPane();
        this.edenGrid.setHgap(5);
        this.edenGrid.setVgap(5);
        this.edenGrid.setBackground(new Background(new BackgroundFill(Constants.EDEN_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
        for (int y = 0; y < Constants.NO_OF_COLS_EDEN; y++) {
            for (int x = 0; x < Constants.NO_OF_ROWS_EDEN; x++) {
                Obj obj = this.collector.getEden().getItemAt(x, y);
                this.edenGrid.add(new ObjRegion(obj), y, x);
            }
        }

        this.survivor1Grid = new GridPane();
        this.survivor1Grid.setHgap(5);
        this.survivor1Grid.setVgap(5);
        this.survivor1Grid.setBackground(new Background(new BackgroundFill(Constants.TO_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
        for (int y = 0; y < Constants.NO_OF_COLS_SURVIVER; y++) {
            for (int x = 0; x < Constants.NO_OF_ROWS_SURVIVER; x++) {
                Obj obj = this.collector.getSurvivor1().getItemAt(x, y);
                this.survivor1Grid.add(new ObjRegion(obj), y, x);
            }
        }

        this.survivor2Grid = new GridPane();
        this.survivor2Grid.setHgap(5);
        this.survivor2Grid.setVgap(5);
        this.survivor2Grid.setBackground(new Background(new BackgroundFill(Constants.FROM_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
        for (int y = 0; y < Constants.NO_OF_COLS_SURVIVER; y++) {
            for (int x = 0; x < Constants.NO_OF_ROWS_SURVIVER; x++) {
                Obj obj = this.collector.getSurvivor2().getItemAt(x, y);
                this.survivor2Grid.add(new ObjRegion(obj), y, x);
            }
        }

        this.tenuredGrid = new GridPane();
        this.tenuredGrid.setHgap(5);
        this.tenuredGrid.setVgap(5);
        this.tenuredGrid.setBackground(new Background(new BackgroundFill(Constants.TENURED_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
        for (int y = 0; y < Constants.NO_OF_COLS_TENURED; y++) {
            for (int x = 0; x < Constants.NO_OF_ROWS_TENURED; x++) {
                Obj obj = this.collector.getTenured().getItemAt(x, y);
                this.tenuredGrid.add(new ObjRegion(obj), y, x);
            }
        }

        edenGrid.setPadding(new Insets(10, 10, 10, 10));
        survivor1Grid.setPadding(new Insets(10, 10, 2.5, 10));
        survivor2Grid.setPadding(new Insets(2.5, 10, 10, 10));
        tenuredGrid.setPadding(new Insets(10));

        GridPane.setRowSpan(edenGrid, 2);
        GridPane.setRowSpan(tenuredGrid, 2);

        this.grid = new GridPane();

        Label edenLabel     = new Label("Eden");
        edenLabel.setAlignment(Pos.CENTER);
        edenLabel.setTextFill(Color.WHITE);
        edenLabel.setFont(Fonts.avenirNextLtProRegular(16));

        Label survivorLabel = new Label("Survivor");
        survivorLabel.setAlignment(Pos.CENTER);
        survivorLabel.setTextFill(Color.WHITE);
        survivorLabel.setFont(Fonts.avenirNextLtProRegular(16));

        Label tenuredLabel  = new Label("Tenured");
        tenuredLabel.setAlignment(Pos.CENTER);
        tenuredLabel.setTextFill(Color.WHITE);
        tenuredLabel.setFont(Fonts.avenirNextLtProRegular(16));


        stateLabel = new Label("");
        stateLabel.setAlignment(Pos.CENTER);
        stateLabel.setTextFill(Color.WHITE);
        stateLabel.setFont(Fonts.avenirNextLtProRegular(16));
        StackPane statePane = new StackPane(stateLabel);
        statePane.setAlignment(Pos.CENTER_LEFT);

        stopImage = new ImageView(new Image(Fonts.class.getResourceAsStream("/eu/hansolo/trashtalk/stop.png")));
        stopImage.setFitWidth(32);
        stopImage.setFitHeight(32);
        stopImage.setVisible(false);
        StackPane stopPane = new StackPane(stopImage);
        stopPane.setAlignment(Pos.CENTER_RIGHT);

        this.grid.add(edenGrid, 0, 0);
        this.grid.add(survivor1Grid, 1, 0);
        this.grid.add(survivor2Grid, 1, 1);
        this.grid.add(tenuredGrid, 2, 0);
        this.grid.add(new StackPane(edenLabel), 0, 2);
        this.grid.add(new StackPane(survivorLabel), 1, 2);
        this.grid.add(new StackPane(tenuredLabel), 2, 2);
        this.grid.add(statePane, 0, 3);
        this.grid.add(stopPane, 2, 3);

        registerListeners();
    }

    private void registerListeners() {
        this.collector.getToggleSurvivorProperty().addListener((o, ov, nv) -> {
            if (nv) {
                // Survivor 2 -> TO-SPACE
                Platform.runLater(() -> {
                    survivor2Grid.setBackground(new Background(new BackgroundFill(Constants.TO_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
                    survivor1Grid.setBackground(new Background(new BackgroundFill(Constants.FROM_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
                });
            } else {
                // Survivor 1 -> TO-SPACE
                Platform.runLater(() -> {
                    survivor1Grid.setBackground(new Background(new BackgroundFill(Constants.TO_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
                    survivor2Grid.setBackground(new Background(new BackgroundFill(Constants.FROM_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
                });
            }
        });
        this.collector.stateTextProperty().addListener(o -> stateLabel.setText(this.collector.getStateText()));
        this.collector.tenuredStateProperty().addListener((o, ov, nv) -> {
            if (GCState.MAJOR_GC == this.collector.getState()) {
                stateLabel.setText(nv.getName());
            }
        });
        this.collector.collectingProperty().addListener((o, ov, nv) -> Platform.runLater(() -> stopImage.setVisible(nv)));
    }

    @Override public void start(final Stage stage) {
        StackPane pane = new StackPane(grid);
        pane.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));
        pane.setPadding(new Insets(20));

        Scene scene = new Scene(pane, 650, 700, Constants.WINDOW_BACKGROUND);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, evt -> {
            if (KeyCode.SPACE.equals(evt.getCode())) {
                this.collector.start();
            }
        });

        stage.setTitle("Generational Mark and Sweep");
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
