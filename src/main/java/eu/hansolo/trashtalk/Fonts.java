package eu.hansolo.trashtalk;

import javafx.scene.text.Font;


public class Fonts {
    private static final String AVENIR_NEXT_LT_PRO_ULTRA_LIGHT_NAME;
    private static final String AVENIR_NEXT_LT_PRO_REGULAR_NAME;
    
    private static String avenirNextLtProUltraLightName;
    private static String avenirNextLtProRegularName;
    
    
    private Fonts() {}


    static {
        try {
            avenirNextLtProUltraLightName = Font.loadFont(Fonts.class.getResourceAsStream("/eu/hansolo/trashtalk/AvenirNextLTPro-UltLt.ttf"), 10).getName();
            avenirNextLtProRegularName    = Font.loadFont(Fonts.class.getResourceAsStream("/eu/hansolo/trashtalk/AvenirNextLTPro-Regular.ttf"), 10).getName();
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        AVENIR_NEXT_LT_PRO_ULTRA_LIGHT_NAME = avenirNextLtProUltraLightName;
        AVENIR_NEXT_LT_PRO_REGULAR_NAME     = avenirNextLtProRegularName;
    }


    // ******************** Methods *******************************************
    public static Font avenirNextLtProLight(final double SIZE) { return new Font(AVENIR_NEXT_LT_PRO_ULTRA_LIGHT_NAME, SIZE); }
    public static Font avenirNextLtProRegular(final double SIZE) { return new Font(AVENIR_NEXT_LT_PRO_REGULAR_NAME, SIZE); }
}
