package eu.hansolo.trashtalk.generationalmarkandcompact;

import javafx.scene.paint.Color;

import java.util.Random;


public class GMCConstants {
    public static final Random RND                             = new Random();
    public static final int       NO_OF_COLS_EDEN              = 2;            // Default 2
    public static final int       NO_OF_ROWS_EDEN              = 20;           // Default 12
    public static final int       NO_OF_COLS_SURVIVER          = 10;           // Default 4
    public static final int       NO_OF_ROWS_SURVIVER          = 10;           // Default 6
    public static final int       NO_OF_COLS_TENURED           = 30;           // Default 8
    public static final int       NO_OF_ROWS_TENURED           = 20;           // Default 12
    public static final long      AGING_INTERVAL               = 100_000_000;  // Interval between calls to object aging (default 500_000_000)
    public static final long      AGING_AMOUNT                 = 1_000;        // Milliseconds that will be subtracted from object lifetime (default 1_000)
    public static final long      ALLOCATION_INTERVAL          = 50_000_000l;  // Interval between memory allocations, (default 250_000_000)
    public static final int       PROMOTION_THRESHOLD          = 15;           // Survived GC runs before get promoted to Tenured (default 3)
    public static final long      PAUSE_TIME                   = 200;          // Time to pause between different actions (default 1_000)
    public static final Integer[] SIZES                        = { 1, 2, 4, 8 };
    public static final Long[]    LIFE_TIMES_IN_MS             = { 500l, 500l, 500l, 500l, 500l, 500l, 500l, 500l, 500l, 500l, 1_000l, 1_000l, 1_000l, 5_000l, 10_000l, 15_000l, 150_000l, 600_000l }; // Object life times to choose from
    public static final Color     FREE_CELL_BACKGROUND         = Color.LIGHTGRAY;
    public static final Color     REFERENCED_CELL_BACKGROUND   = Color.web("#3F8FBC");
    public static final Color     DEREFERENCED_CELL_BACKGROUND = Color.web("#925CFF");
    public static final Color     MARKED_COLOR                 = Color.web("#FF2B60");
    public static final Color     WINDOW_BACKGROUND            = Color.web("#162241");
    public static final Color     EDEN_BACKGROUND              = Color.web("#FADC5A");
    public static final Color     FROM_BACKGROUND              = Color.web("#808080");
    public static final Color     TO_BACKGROUND                = Color.web("#EF8A34");
    public static final Color     TENURED_BACKGROUND           = Color.web("#AAD8EF");
}
