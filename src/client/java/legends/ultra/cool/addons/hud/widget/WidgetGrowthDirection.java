package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.hud.HudWidget;

import java.util.List;

enum WidgetGrowthDirection {
    UP("up", "Up"),
    CENTER("center", "Center"),
    DOWN("down", "Down");

    static final String SETTING_KEY = "growthDirection";
    static final String DEFAULT_ID = "up";

    private static final List<HudWidget.HudOption> OPTIONS = List.of(
            HudWidget.HudOption.of(UP.id, UP.label),
            HudWidget.HudOption.of(CENTER.id, CENTER.label),
            HudWidget.HudOption.of(DOWN.id, DOWN.label)
    );

    private final String id;
    private final String label;

    WidgetGrowthDirection(String id, String label) {
        this.id = id;
        this.label = label;
    }

    static WidgetGrowthDirection fromId(String id) {
        if (id == null) {
            return UP;
        }

        for (WidgetGrowthDirection direction : values()) {
            if (direction.id.equalsIgnoreCase(id)) {
                return direction;
            }
        }
        return UP;
    }

    static List<HudWidget.HudOption> options() {
        return OPTIONS;
    }

    int topY(double anchorY, int height, int anchorRowHeight) {
        return switch (this) {
            case UP -> (int) anchorY - Math.max(0, height - anchorRowHeight);
            case CENTER -> (int) Math.round(anchorY - Math.max(0, height - anchorRowHeight) / 2d);
            case DOWN -> (int) anchorY;
        };
    }
}
