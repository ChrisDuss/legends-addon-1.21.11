package legends.ultra.cool.addons.hud.widget.otherTypes;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudWidget;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class NameplateWidget extends HudWidget {

    // Simple global access for mixins (no new systems)
    public static NameplateWidget INSTANCE;

    public NameplateWidget() {
        super("Nameplates", 0, 0);
        INSTANCE = this;
    }

    public static boolean isEnabledGlobal() {
        return INSTANCE != null && INSTANCE.isEnabled();
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

