package eu.hansolo.trashtalk.markandsweep;


import javafx.scene.paint.Color;

import java.util.Random;


public class Constants {
    public static final Random    RND                          = new Random();
    public static final int       NO_OF_ROWS                   = 14;
    public static final int       NO_OF_COLS                   = 12;
    public static final long      AGING_INTERVAL               = 1_000_000_000;
    public static final long      AGING_AMOUNT                 = 2_000;
    public static final long      ALLOCATION_INTERVAL          = 250_000_000l;
    public static final long      PAUSE_TIME                   = 1_000;
    public static final Integer[] SIZES                        = { 2, 4 };
    public static final Long[]    LIFE_TIMES_IN_MS             = { 1_000l, 2_000l, 5_000l, 40_000l };
    public static final Color     FREE_CELL_BACKGROUND         = Color.LIGHTGRAY;
    public static final Color     REFERENCED_CELL_BACKGROUND   = Color.web("#3F8FBC");
    public static final Color     DEREFERENCED_CELL_BACKGROUND = Color.web("#925CFF");
    public static final Color     MARKED_COLOR                 = Color.web("#FF2B60");
    public static final Color     WINDOW_BACKGROUND            = Color.web("#162241");
}
