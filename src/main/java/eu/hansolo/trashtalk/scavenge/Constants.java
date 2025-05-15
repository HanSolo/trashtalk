package eu.hansolo.trashtalk.scavenge;

import javafx.scene.paint.Color;

import java.util.Random;


public class Constants {
    public static final Random    RND                          = new Random();
    public static final int       NO_OF_COLS_SURVIVER          = 7;            // Default 7
    public static final int       NO_OF_ROWS_SURVIVER          = 6;            // Default 6
    public static final int       NO_OF_COLS_TENURED           = 8;            // Default 8
    public static final int       NO_OF_ROWS_TENURED           = 12;           // Default 12
    public static final long      AGING_INTERVAL               = 500_000_000;  // Interval between calls to object aging (default 500_000_000)
    public static final long      AGING_AMOUNT                 = 1_000;        // Milliseconds that will be subtracted from object lifetime (default 1_000)
    public static final long      ALLOCATION_INTERVAL          = 250_000_000l; // Interval between memory allocations, (default 250_000_000)
    public static final int       PROMOTION_THRESHOLD          = 3;            // Survived GC runs before get promoted to Tenured (default 3)
    public static final long      PAUSE_TIME                   = 1_000;        // Time to pause between different actions (default 1_000)
    public static final Integer[] SIZES                        = { 1, 2 };
    public static final Long[]    LIFE_TIMES_IN_MS             = { 1_000l, 2_000l, 5_000l, 40_000l }; // Object life times to choose from
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
