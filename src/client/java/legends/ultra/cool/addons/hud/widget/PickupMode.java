package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.hud.HudWidget;

import java.util.List;

public enum PickupMode {
    DIFF("diff", "Diff"),
    REPLACE("replace", "Replace"),
    NONE("none", "None");

    static final String SETTING_KEY = "pickupMode";
    static final String DEFAULT_ID = "diff";

    private static final List<HudWidget.HudOption> OPTIONS = List.of(
            HudWidget.HudOption.of(DIFF.id, DIFF.label),
            HudWidget.HudOption.of(REPLACE.id, REPLACE.label),
            HudWidget.HudOption.of(NONE.id, NONE.label)
    );

    private final String id;
    private final String label;

    PickupMode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    static PickupMode fromId(String id) {
        if (id == null) {
            return DIFF;
        }

        for (PickupMode mode : values()) {
            if (mode.id.equalsIgnoreCase(id)) {
                return mode;
            }
        }
        return DIFF;
    }

    static List<HudWidget.HudOption> options() {
        return OPTIONS;
    }
}
