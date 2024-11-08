package eu.hansolo.trashtalk.generationalmarkandcompact;

import javafx.beans.DefaultProperty;
import javafx.collections.ObservableList;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;


/**
 * User: hansolo
 * Date: 08.12.23
 * Time: 17:02
 */
@DefaultProperty("children")
public class ObjRegion extends Region {
    private static final double          PREFERRED_WIDTH  = 35;
    private static final double          PREFERRED_HEIGHT = 50;
    private static final double          MINIMUM_WIDTH    = 25;
    private static final double          MINIMUM_HEIGHT   = 40;
    private static final double          MAXIMUM_WIDTH    = 250;
    private static final double          MAXIMUM_HEIGHT   = 400;
    private              double          aspectRatio;
    private              double          width;
    private              double          height;
    private              Canvas          canvas;
    private GraphicsContext ctx;
    private Obj             obj;


    // ******************** Constructors **************************************
    public ObjRegion(final Obj obj) {
        this.obj         = obj;
        this.aspectRatio = PREFERRED_HEIGHT / PREFERRED_WIDTH;
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
        this.obj.stateProperty().addListener(e -> redraw());
        this.obj.markedProperty().addListener(e -> redraw());
        this.obj.reMarkedProperty().addListener(e -> redraw());
        this.obj.readBarrierProperty().addListener(e -> redraw());
        this.obj.writeBarrierProperty().addListener(e -> redraw());
        this.obj.survivedGarbageCollectionsProperty().addListener(e -> redraw());
        this.obj.lifeSpanInMilliSecondsProperty().addListener(e -> redraw());
    }


    // ******************** Methods *******************************************
    @Override protected double computeMinWidth(final double height) { return MINIMUM_WIDTH; }
    @Override protected double computeMinHeight(final double width)  { return MINIMUM_HEIGHT; }
    @Override protected double computePrefWidth(final double height) { return super.computePrefWidth(height); }
    @Override protected double computePrefHeight(final double width) { return super.computePrefHeight(width); }
    @Override protected double computeMaxWidth(final double height)  { return MAXIMUM_WIDTH; }
    @Override protected double computeMaxHeight(final double width)  { return MAXIMUM_HEIGHT; }

    @Override public ObservableList<Node> getChildren()              { return super.getChildren(); }


    // ******************** Layout *******************************************
    @Override public void layoutChildren() {
        super.layoutChildren();
    }

    private void resize() {
        width  = getWidth() - getInsets().getLeft() - getInsets().getRight();
        height = getHeight() - getInsets().getTop() - getInsets().getBottom();

        if (aspectRatio * width > height) {
            width = 1 / (aspectRatio / height);
        } else if (1 / (aspectRatio / height) > width) {
            height = aspectRatio * width;
        }

        if (width > 0 && height > 0) {
            canvas.setWidth(width);
            canvas.setHeight(height);
            canvas.relocate((getWidth() - width) * 0.5, (getHeight() - height) * 0.5);

            redraw();
        }
    }

    public void redraw() {
        ctx.clearRect(0, 0, width, height);
        double headerBitWidth  = width * 0.25;
        double headerBitHeight = height * 0.2;

        // Fill obj related to state
        switch(obj.getState()) {
            case FREE         -> ctx.setFill(Constants.FREE_CELL_BACKGROUND);
            case REFERENCED   -> ctx.setFill(Constants.REFERENCED_CELL_BACKGROUND);
            case DEREFERENCED -> ctx.setFill(Constants.DEREFERENCED_CELL_BACKGROUND);
            default           -> ctx.setFill(Constants.FREE_CELL_BACKGROUND);
        }
        ctx.fillRect(0, headerBitHeight, width, height);

        // Marked
        ctx.setFill(obj.isMarked() ? Constants.MARKED_COLOR : Color.WHITE);
        ctx.fillRect(0, 0, headerBitWidth, headerBitHeight);

        // ReMarked
        ctx.setFill(obj.isReMarked() ? Color.GREEN : Color.WHITE);
        ctx.fillRect(headerBitWidth, 0, headerBitWidth, headerBitHeight);

        // ReadBarrier
        ctx.setFill(obj.getReadBarrier() ? Color.RED : Color.WHITE);
        ctx.fillRect(2 * headerBitWidth, 0, headerBitWidth, headerBitHeight);

        // WriteBarrier
        ctx.setFill(obj.getWriteBarrier() ? Color.MAGENTA : Color.WHITE);
        ctx.fillRect(3 * headerBitWidth, 0, headerBitWidth, headerBitHeight);


        // Draw Object frames
        ctx.setStroke(Color.BLACK);
        ctx.strokeRect(0, 0, width, height);
        ctx.strokeLine(0, headerBitHeight, width, headerBitHeight);
        for (int i = 1 ; i < 4 ; i++) {
            ctx.strokeLine(i * headerBitWidth, 0, i * headerBitWidth, headerBitHeight);
        }

        // Draw survived garbage collection counter if > 0
        if (obj.getSurvivedGarbageCollections() > 0) {
            ctx.setFill(Color.WHITE);
            ctx.setFont(Font.font((height - headerBitHeight) * 0.5));
            ctx.setTextBaseline(VPos.CENTER);
            ctx.setTextAlign(TextAlignment.CENTER);
            ctx.fillText(Integer.toString(obj.getSurvivedGarbageCollections()), width * 0.5, (height - headerBitHeight) * 0.5 + headerBitHeight);
        }
    }
}