package eu.hansolo.trashtalk.regionbased;


import javafx.scene.paint.Color;

import java.util.Random;


public class Constants {
    public enum ObjRegionType     {
            FREE(Color.TRANSPARENT, Color.color(1.0, 1.0, 1.0, 0.5)),
            EDEN(Color.color(0.99, 0.84, 0.17, 1.00), Color.color(0.99, 0.84, 0.17, 1.00)),
            SURVIVOR(Color.color(0.99, 0.47, 0.00, 1.00), Color.color(0.99, 0.47, 0.00, 1.00)),
            TENURED(Color.color(0.57, 0.84, 0.94, 1.00), Color.color(0.57, 0.84, 0.94, 1.00)),
            HUMONGOUS(Color.color(0.22, 0.52, 0.71, 1.00), Color.color(0.22, 0.52, 0.71, 1.00));

        public final Color fill;
        public final Color stroke;

        ObjRegionType(final Color fill, final Color stroke) {
            this.fill   = fill;
            this.stroke = stroke;
        }
    }

    public static final Random    RND                          = new Random();
    public static final int       NO_OF_ROWS                   = 8;
    public static final int       NO_OF_COLS                   = 8;
    public static final int       REGION_COLS                  = 8;
    public static final int       REGION_ROWS                  = 8;
    public static final int       REGION_SIZE                  = REGION_COLS * REGION_ROWS;
    public static final long      AGING_INTERVAL               = 1_000_000_000;
    public static final long      AGING_AMOUNT                 = 1_000;
    public static final long      ALLOCATION_INTERVAL          = 250_000_000l;
    public static final long      PAUSE_TIME                   = 500;
    public static final Integer[] SIZES                        = { 2, 4, 8, 16 };
    public static final Long[]    LIFE_TIMES_IN_MS             = { 5_000l, 10_000l, 15_000l, 60_000l, 480_000l, 1_920_000l };
    public static final Color     WINDOW_BACKGROUND            = Color.web("#162241");
    public static final int       MAX_NO_OF_EDEN_REGIONS       = 6;
    public static final int       MAX_NO_OF_SURVIVOR_REGIONS   = 3;
}
