package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.hud.HudWidget;

import java.util.List;

enum TextAlignment {
    LEFT("left", "Left"),
    CENTER("center", "Center"),
    RIGHT("right", "Right");

    static final String SETTING_KEY = "textAlignment";
    static final String DEFAULT_ID = "center";

    private static final List<HudWidget.HudOption> OPTIONS = List.of(
            HudWidget.HudOption.of(LEFT.id, LEFT.label),
            HudWidget.HudOption.of(CENTER.id, CENTER.label),
            HudWidget.HudOption.of(RIGHT.id, RIGHT.label)
    );

    private final String id;
    private final String label;

    TextAlignment(String id, String label) {
        this.id = id;
        this.label = label;
    }

    static TextAlignment fromId(String id) {
        if (id == null) {
            return CENTER;
        }

        for (TextAlignment alignment : values()) {
            if (alignment.id.equalsIgnoreCase(id)) {
                return alignment;
            }
        }
        return CENTER;
    }

    static List<HudWidget.HudOption> options() {
        return OPTIONS;
    }

    double alignedX(double x, double oldWidth, double newWidth) {
        return switch (this) {
            case LEFT -> x;
            case CENTER -> x + oldWidth / 2d - newWidth / 2d;
            case RIGHT -> x + oldWidth - newWidth;
        };
    }
}
