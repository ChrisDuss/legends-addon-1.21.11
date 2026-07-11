package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudWidget;

import java.util.List;

enum TextAlignment {
    LEFT("left", "Left"),
    CENTER("center", "Center"),
    RIGHT("right", "Right");

    static final String SETTING_KEY = "textAlignment";
    static final String DEFAULT_ID = "center";
    static final String LEFT_DEFAULT_ID = "left";

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

    double leftX(double anchorX, double width) {
        return switch (this) {
            case LEFT -> anchorX;
            case CENTER -> anchorX - width / 2d;
            case RIGHT -> anchorX - width;
        };
    }

    double anchorXForLeft(double leftX, double width) {
        return switch (this) {
            case LEFT -> leftX;
            case CENTER -> leftX + width / 2d;
            case RIGHT -> leftX + width;
        };
    }

    static void setForWidgetPreservingLeft(HudWidget widget, String nextId, String defaultId) {
        setForWidgetPreservingLeft(widget, nextId, defaultId, widget.getWidth());
    }

    static void setForWidgetPreservingLeft(HudWidget widget, String nextId, String defaultId, double width) {
        TextAlignment current = fromId(WidgetConfigManager.getString(widget.getName(), SETTING_KEY, defaultId));
        TextAlignment next = fromId(nextId);
        double left = current.leftX(widget.x, width);

        widget.x = next.anchorXForLeft(left, width);
        WidgetConfigManager.setString(widget.getName(), SETTING_KEY, next.id, false);
        WidgetConfigManager.updateWidget(widget);
    }
}
