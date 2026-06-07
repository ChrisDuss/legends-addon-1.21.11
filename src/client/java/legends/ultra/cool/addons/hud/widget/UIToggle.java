package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudWidget;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class UIToggle extends HudWidget {
    public static final String WIDGET_NAME = "UIToggle";
    private static final String HIDE_HEALTH_BAR_KEY = "hideHealthBar";
    private static final String HIDE_FOOD_BAR_KEY = "hideFoodBar";
    private static final String HIDE_XP_BAR_KEY = "hideXpBar";

    private static UIToggle INSTANCE;

    public UIToggle() {
        super(WIDGET_NAME, 0, 0);
        enabled = true;
        INSTANCE = this;
    }

    public static boolean isEnabledGlobal() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    public static boolean shouldHideHealthBar() {
        return isEnabledGlobal() && WidgetConfigManager.getBool(WIDGET_NAME, HIDE_HEALTH_BAR_KEY, legacyHealthBarHidden());
    }

    public static boolean shouldHideFoodBar() {
        return isEnabledGlobal() && WidgetConfigManager.getBool(WIDGET_NAME, HIDE_FOOD_BAR_KEY, legacyFoodBarHidden());
    }

    public static boolean shouldHideXpBar() {
        return isEnabledGlobal() && WidgetConfigManager.getBool(WIDGET_NAME, HIDE_XP_BAR_KEY, false);
    }

    @Override
    public void render(DrawContext context) {
    }

    @Override
    public double getWidth() {
        return 0;
    }

    @Override
    public double getHeight() {
        return 0;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public List<HudSetting> getSettings() {
        final String w = getName();
        boolean healthDefault = legacyHealthBarHidden();
        boolean foodDefault = legacyFoodBarHidden();

        return List.of(
                HudSetting.section("Vanilla UI"),
                HudSetting.toggle(HIDE_HEALTH_BAR_KEY, "Hide health bar",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, HIDE_HEALTH_BAR_KEY, healthDefault),
                        b -> WidgetConfigManager.setBool(w, HIDE_HEALTH_BAR_KEY, b, true),
                        healthDefault
                ),
                HudSetting.toggle(HIDE_FOOD_BAR_KEY, "Hide food bar",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, HIDE_FOOD_BAR_KEY, foodDefault),
                        b -> WidgetConfigManager.setBool(w, HIDE_FOOD_BAR_KEY, b, true),
                        foodDefault
                ),
                HudSetting.toggle(HIDE_XP_BAR_KEY, "Hide XP bar",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, HIDE_XP_BAR_KEY, false),
                        b -> WidgetConfigManager.setBool(w, HIDE_XP_BAR_KEY, b, true),
                        false
                )
        );
    }

    private static boolean legacyHealthBarHidden() {
        return WidgetConfigManager.getBool("Health Display", "heartsToggle", false);
    }

    private static boolean legacyFoodBarHidden() {
        return WidgetConfigManager.getBool("Mana Display", "foodToggle", false);
    }
}
