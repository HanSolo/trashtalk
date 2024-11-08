package eu.hansolo.trashtalk.regionbased;

import eu.hansolo.toolbox.observables.ObservableMatrix;
import eu.hansolo.toolbox.tuples.Pair;
import eu.hansolo.trashtalk.CellState;
import eu.hansolo.trashtalk.regionbased.Constants.ObjRegionType;
import javafx.beans.DefaultProperty;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;


/**
 * User: hansolo
 * Date: 08.12.23
 * Time: 17:02
 */
@DefaultProperty("children")
public class ObjRegion extends Region {
    private static final double                PREFERRED_WIDTH  = 64;
    private static final double                PREFERRED_HEIGHT = 64;
    private static final double                MINIMUM_WIDTH    = 40;
    private static final double                MINIMUM_HEIGHT   = 40;
    private static final double                MAXIMUM_WIDTH    = 400;
    private static final double                MAXIMUM_HEIGHT   = 400;
    private              ObjRegionType         type;
    private              double                width;
    private              double                height;
    private              double                size;
    private              double                cellSize;
    private              Canvas                canvas;
    private              GraphicsContext       ctx;
    private              boolean               selectedForGC;
    private              ObservableMatrix<Obj> objMatrix;


    // ******************** Constructors **************************************
    public ObjRegion() {
        this(ObjRegionType.FREE);
    }
    public ObjRegion(final ObjRegionType type) {
        this.objMatrix     = new ObservableMatrix<>(Obj.class, Constants.REGION_COLS, Constants.REGION_ROWS);
        this.type          = type;
        this.selectedForGC = false;

        for (int ry = 0 ; ry < Constants.REGION_ROWS ; ry++) {
            for (int rx = 0 ; rx < Constants.REGION_COLS ; rx++) {
                this.objMatrix.setItemAt(rx, ry, new Obj());
            }
        }

        initGraphics();
        registerListeners();
    }


    // ******************** Initialization ************************************
    private void initGraphics() {
        if (Double.compare(getPrefWidth(), 0.0) <= 0 || Double.compare(getPrefHeight(), 0.0) <= 0 || Double.compare(getWidth(), 0.0) <= 0 || Double.compare(getHeight(), 0.0) <= 0) {
            if (getPrefWidth() > 0 && getPrefHeight() > 0) {
                setPrefSize(getPrefWidth(), getPrefHeight());
            } else {
                setPrefSize(PREFERRED_WIDTH, PREFERRED_HEIGHT);
            }
        }

        canvas = new Canvas(PREFERRED_WIDTH, PREFERRED_HEIGHT);
        ctx    = canvas.getGraphicsContext2D();

        getChildren().setAll(canvas);
    }

    private void registerListeners() {
        this.widthProperty().addListener(e -> resize());
        this.heightProperty().addListener(e -> resize());
        this.objMatrix.getAllItems().forEach(obj -> {
            obj.get().stateProperty().addListener(e -> redraw());
            obj.get().survivedGarbageCollectionsProperty().addListener(e -> redraw());
            obj.get().lifeSpanInMilliSecondsProperty().addListener(e -> redraw());
        });
    }


    // ******************** Methods *******************************************
    @Override protected double computeMinWidth(final double height) { return MINIMUM_WIDTH; }
    @Override protected double computeMinHeight(final double width) { return MINIMUM_HEIGHT; }
    @Override protected double computePrefWidth(final double height) { return super.computePrefWidth(height); }
    @Override protected double computePrefHeight(final double width) { return super.computePrefHeight(width); }
    @Override protected double computeMaxWidth(final double height) { return MAXIMUM_WIDTH; }
    @Override protected double computeMaxHeight(final double width) { return MAXIMUM_HEIGHT; }

    @Override public ObservableList<Node> getChildren() { return super.getChildren(); }

    public ObservableMatrix<Obj> getObjMatrix() { return this.objMatrix; }

    public ObjRegionType getType() { return this.type; }
    public void setType(final ObjRegionType type) {
        this.type = type;
        redraw();
    }

    public boolean isFreeSpaceAvailable() {
        long numberOfFreeCellsAvailable = objMatrix.getAllItems().stream().filter(ref -> ref.get().getState() == CellState.FREE).count();
        return numberOfFreeCellsAvailable > 0 && numberOfFreeCellsAvailable <= Constants.REGION_SIZE;
    }

    public long getAmountOfGarbage() {
        return this.objMatrix.getAllItems().stream().filter(ref -> CellState.DEREFERENCED == ref.get().getState()).count();
    }

    public boolean isCompletelyGarbage() { return getAmountOfGarbage() == Constants.REGION_SIZE; }

    public Pair<Integer, Integer> getNextFreeCellIndices() {
        return getNextCellIndicesForState(CellState.FREE);
    }
    public Pair<Integer, Integer> getNextLiveCellIndices() {
        return getNextCellIndicesForState(CellState.REFERENCED);
    }
    public Pair<Integer, Integer> getNextDeadCellIndices() {
        return getNextCellIndicesForState(CellState.DEREFERENCED);
    }
    public Pair<Integer, Integer> getNextCellIndicesForState(final CellState state) {
        for (int ry = 0 ; ry < Constants.REGION_ROWS ; ry++) {
            for (int rx = 0 ; rx < Constants.REGION_COLS ; rx++) {
                if (state == objMatrix.getItemAt(rx, ry).getState()) {
                    return new Pair<>(rx, ry);
                }
            }
        }
        return new Pair<>(0, 0);
    }

    public void copyNextLiveCell() {
        Pair<Integer, Integer> indices = getNextLiveCellIndices();
        objMatrix.getItemAt(indices.getA(), indices.getB()).setState(CellState.DEREFERENCED);
    }

    public void freeRegion() {
        this.selectedForGC = false;
        this.objMatrix.getAllItems().forEach(ref -> ref.get().setState(CellState.FREE));
        this.setType(ObjRegionType.FREE);
    }

    public static void delay(long millis, Runnable continuation) {
        Task<Void> sleeper = new Task<Void>() {
            @Override protected Void call() throws Exception {
                try { Thread.sleep(millis); }
                catch (InterruptedException e) { }
                return null;
            }
        };
        sleeper.setOnSucceeded(event -> continuation.run());
        new Thread(sleeper).start();
    }


    // ******************** Layout *******************************************
    @Override public void layoutChildren() {
        super.layoutChildren();
    }

    private void resize() {
        width    = getWidth() - getInsets().getLeft() - getInsets().getRight();
        height   = getHeight() - getInsets().getTop() - getInsets().getBottom();
        size     = width < height ? width : height;
        cellSize = size / Constants.REGION_COLS;

        if (width > 0 && height > 0) {
            canvas.setWidth(size);
            canvas.setHeight(size);
            canvas.relocate((getWidth() - width) * 0.5, (getHeight() - height) * 0.5);

            redraw();
        }
    }

    public boolean redraw() {
        ctx.clearRect(0, 0, size, size);

        // Set type dependent fill and stroke
        Color typeFill             = this.type.fill;
        Color typeStroke           = this.type.stroke;
        Color freeCellFill         = Color.TRANSPARENT;
        Color freeCellStroke       = typeStroke;
        Color referencedCellFill   = typeFill;
        Color referencedCellStroke = typeStroke.darker();
        Color derefencedCellFill   = typeFill.darker().darker();
        Color derefencedCellStroke = typeStroke;//.darker();


        // Draw cell dependent state
        ctx.setLineWidth(0.5);
        for (int ry = 0 ;  ry < Constants.REGION_ROWS ; ry++) {
            for (int rx = 0 ; rx < Constants.REGION_COLS ; rx++) {
                Obj obj = objMatrix.getItemAt(rx, ry);
                // Draw survived garbage collection counter if > 0
                if (obj.getSurvivedGarbageCollections() > 0) {

                }
                // Fill obj related to state
                switch(obj.getState()) {
                    case FREE         -> {
                        ctx.setFill(freeCellFill);
                        ctx.setStroke(freeCellStroke);
                    }
                    case REFERENCED   -> {
                        ctx.setFill(referencedCellFill);
                        ctx.setStroke(referencedCellStroke);
                    }
                    case DEREFERENCED -> {
                        ctx.setFill(derefencedCellFill);
                        ctx.setStroke(derefencedCellStroke);
                    }
                    default           -> {
                        ctx.setFill(freeCellFill);
                        ctx.setStroke(freeCellStroke);
                    }
                }
                ctx.fillRect(rx * cellSize, ry * cellSize, cellSize, cellSize);
                ctx.strokeRect(rx * cellSize, ry * cellSize, cellSize, cellSize);
            }
        }

        // Draw Object frames
        ctx.setLineWidth(1.0);
        ctx.setStroke(Color.WHITE);
        ctx.strokeRect(0, 0, width, height);
        return true;
    }
}