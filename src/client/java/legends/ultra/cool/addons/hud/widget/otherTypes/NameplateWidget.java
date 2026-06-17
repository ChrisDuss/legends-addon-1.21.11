package legends.ultra.cool.addons.hud.widget.otherTypes;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudWidget;
import legends.ultra.cool.addons.util.AddonServerGate;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class NameplateWidget extends HudWidget {
    private static final String SHOW_NAME_KEY = "showName";
    private static final String SHOW_BAR_KEY = "showBar";
    private static final String SHOW_STATS_KEY = "showStats";
    private static final boolean DEFAULT_SHOW_NAME = true;
    private static final boolean DEFAULT_SHOW_BAR = true;
    private static final boolean DEFAULT_SHOW_STATS = true;

    // Simple global access for mixins (no new systems)
    public static NameplateWidget INSTANCE;

    public NameplateWidget() {
        super("Nameplates", 0, 0);
        INSTANCE = this;
    }

    public static boolean isEnabledGlobal() {
        return AddonServerGate.shouldRunOnCurrentServer() && INSTANCE != null && INSTANCE.isEnabled();
    }

    public static boolean shouldShowNameText() {
        return isEnabledGlobal() && WidgetConfigManager.getBool(INSTANCE.getName(), SHOW_NAME_KEY, DEFAULT_SHOW_NAME);
    }

    public static boolean shouldShowHealthBar() {
        return isEnabledGlobal() && WidgetConfigManager.getBool(INSTANCE.getName(), SHOW_BAR_KEY, DEFAULT_SHOW_BAR);
    }

    public static boolean shouldShowStatsText() {
        return isEnabledGlobal() && WidgetConfigManager.getBool(INSTANCE.getName(), SHOW_STATS_KEY, DEFAULT_SHOW_STATS);
    }

    public static void setShowNameText(boolean value) {
        WidgetConfigManager.setBool(INSTANCE.getName(), SHOW_NAME_KEY, value, true);
    }

    public static void setShowHealthBar(boolean value) {
        WidgetConfigManager.setBool(INSTANCE.getName(), SHOW_BAR_KEY, value, true);
    }

    public static void setShowStatsText(boolean value) {
        WidgetConfigManager.setBool(INSTANCE.getName(), SHOW_STATS_KEY, value, true);
    }

    @Override
    public void render(DrawContext context) {
        // Intentionally empty: this widget is just a toggle/config handle
    }

    @Override
    public double getWidth() {
        return 1; // non-interactive on canvas
    }

    @Override
    public double getHeight() {
        return 1;
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public List<HudSetting> getSettings() {
        final String w = this.getName(); // or a constant like "Nameplates"

        return List.of(
                HudSetting.section("Content"),
                HudSetting.toggle(
                        SHOW_NAME_KEY, "Show name",
                        () -> true,
                        NameplateWidget::shouldShowNameText,
                        NameplateWidget::setShowNameText,
                        DEFAULT_SHOW_NAME
                ),
                HudSetting.toggle(
                        SHOW_BAR_KEY, "Show health bar",
                        () -> true,
                        NameplateWidget::shouldShowHealthBar,
                        NameplateWidget::setShowHealthBar,
                        DEFAULT_SHOW_BAR
                ),
                HudSetting.toggle(
                        SHOW_STATS_KEY, "Show stats",
                        () -> true,
                        NameplateWidget::shouldShowStatsText,
                        NameplateWidget::setShowStatsText,
                        DEFAULT_SHOW_STATS
                ),
                HudSetting.slider(
                        "yOffset", "Height",
                        0f, 2f, 0.05f,
                        () -> true,
                        () -> WidgetConfigManager.getFloat(w, "yOffset", 1f),
                        v -> WidgetConfigManager.setFloat(w, "yOffset", (float) v, true),
                        1f
                ),
                HudSetting.slider(
                        "scale", "Scale",
                        0.1f, 2f, 0.05f,
                        () -> true,
                        () -> WidgetConfigManager.getFloat(w, "scale", 1f),
                        v -> WidgetConfigManager.setFloat(w, "scale", (float) v, true),
                        1f
                ),
                HudSetting.slider(
                        "range", "Block range",
                        1f, 100f, 1f,
                        () -> true,
                        () -> WidgetConfigManager.getFloat(w, "range", 50f),
                        v -> WidgetConfigManager.setFloat(w, "range", (float) v, true),
                        50f
                ),
                HudSetting.color(
                        "bgColor", "Nameplate Color",
                        () -> true,
                        () -> WidgetConfigManager.getInt(w, "bgColor", 0xFFFF0016),
                        c -> WidgetConfigManager.setInt(w, "bgColor", c, true),
                        0xFFFF0016
                )
        );
    }
}

