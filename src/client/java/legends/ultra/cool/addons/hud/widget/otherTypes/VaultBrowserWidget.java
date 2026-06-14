package legends.ultra.cool.addons.hud.widget.otherTypes;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudWidget;
import legends.ultra.cool.addons.util.AddonServerGate;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public final class VaultBrowserWidget extends HudWidget {
    public static final String WIDGET_NAME = "Vault Browser";
    private static final String AUTO_OPEN_FROM_STORAGE_MENU_KEY = "autoOpenFromStorageMenu";
    private static final String AUTO_OPEN_AFTER_LOAD_KEY = "autoOpenAfterLoad";
    private static final String SHOW_BROWSER_HINT_KEY = "showBrowserHint";
    private static final String ALWAYS_SHOW_VAULT_NUMBER_KEY = "alwaysShowVaultNumber";
    private static final String SCALE_KEY = "scale";
    private static final String WARDROBE_SCALE_KEY = "wardrobeScale";
    private static final boolean DEFAULT_AUTO_OPEN_FROM_STORAGE_MENU = false;
    private static final boolean DEFAULT_AUTO_OPEN_AFTER_LOAD = true;
    private static final boolean DEFAULT_SHOW_BROWSER_HINT = true;
    private static final boolean DEFAULT_ALWAYS_SHOW_VAULT_NUMBER = false;
    private static final float DEFAULT_SCALE = 1.0f;
    private static final float DEFAULT_WARDROBE_SCALE = 1.0f;

    private static VaultBrowserWidget INSTANCE;

    public VaultBrowserWidget() {
        super(WIDGET_NAME, 0, 0);
        this.enabled = true;
        INSTANCE = this;
    }

    public static boolean isEnabledGlobal() {
        return AddonServerGate.shouldRunOnCurrentServer() && INSTANCE != null && INSTANCE.isEnabled();
    }

    public static boolean shouldAutoOpenFromStorageMenu() {
        return isEnabledGlobal() && getAutoOpenFromStorageMenuSetting();
    }

    public static boolean shouldAutoOpenAfterLoad() {
        return isEnabledGlobal() && getAutoOpenAfterLoadSetting();
    }

    public static boolean shouldShowBrowserHint() {
        return isEnabledGlobal() && getShowBrowserHintSetting();
    }

    public static boolean shouldAlwaysShowVaultNumber() {
        return isEnabledGlobal() && getAlwaysShowVaultNumberSetting();
    }

    public static float getScaleSetting() {
        return WidgetConfigManager.getFloat(WIDGET_NAME, SCALE_KEY, DEFAULT_SCALE);
    }

    public static float getWardrobeScaleSetting() {
        return WidgetConfigManager.getFloat(WIDGET_NAME, WARDROBE_SCALE_KEY, DEFAULT_WARDROBE_SCALE);
    }

    public static boolean getAutoOpenFromStorageMenuSetting() {
        return WidgetConfigManager.getBool(WIDGET_NAME, AUTO_OPEN_FROM_STORAGE_MENU_KEY, DEFAULT_AUTO_OPEN_FROM_STORAGE_MENU);
    }

    public static boolean getAutoOpenAfterLoadSetting() {
        return WidgetConfigManager.getBool(WIDGET_NAME, AUTO_OPEN_AFTER_LOAD_KEY, DEFAULT_AUTO_OPEN_AFTER_LOAD);
    }

    public static boolean getShowBrowserHintSetting() {
        return WidgetConfigManager.getBool(WIDGET_NAME, SHOW_BROWSER_HINT_KEY, DEFAULT_SHOW_BROWSER_HINT);
    }

    public static boolean getAlwaysShowVaultNumberSetting() {
        return WidgetConfigManager.getBool(WIDGET_NAME, ALWAYS_SHOW_VAULT_NUMBER_KEY, DEFAULT_ALWAYS_SHOW_VAULT_NUMBER);
    }

    public static void setAutoOpenFromStorageMenuSetting(boolean value) {
        WidgetConfigManager.setBool(WIDGET_NAME, AUTO_OPEN_FROM_STORAGE_MENU_KEY, value, true);
    }

    public static void setAutoOpenAfterLoadSetting(boolean value) {
        WidgetConfigManager.setBool(WIDGET_NAME, AUTO_OPEN_AFTER_LOAD_KEY, value, true);
    }

    public static void setShowBrowserHintSetting(boolean value) {
        WidgetConfigManager.setBool(WIDGET_NAME, SHOW_BROWSER_HINT_KEY, value, true);
    }

    public static void setAlwaysShowVaultNumberSetting(boolean value) {
        WidgetConfigManager.setBool(WIDGET_NAME, ALWAYS_SHOW_VAULT_NUMBER_KEY, value, true);
    }

    public static void setScaleSetting(float value) {
        WidgetConfigManager.setFloat(WIDGET_NAME, SCALE_KEY, value, true);
    }

    public static void setWardrobeScaleSetting(float value) {
        WidgetConfigManager.setFloat(WIDGET_NAME, WARDROBE_SCALE_KEY, value, true);
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
        return List.of(
                HudSetting.section("Vault Browser"),
                HudSetting.toggle("autoOpenFromStorageMenu", "Auto-open from Menu",
                        () -> true,
                        VaultBrowserWidget::getAutoOpenFromStorageMenuSetting,
                        VaultBrowserWidget::setAutoOpenFromStorageMenuSetting,
                        DEFAULT_AUTO_OPEN_FROM_STORAGE_MENU
                ),
                HudSetting.toggle("autoOpenAfterLoad", "Open after load or reload",
                        () -> true,
                        VaultBrowserWidget::getAutoOpenAfterLoadSetting,
                        VaultBrowserWidget::setAutoOpenAfterLoadSetting,
                        DEFAULT_AUTO_OPEN_AFTER_LOAD
                ),
                HudSetting.toggle("showBrowserHint", "Show browser hint",
                        () -> true,
                        VaultBrowserWidget::getShowBrowserHintSetting,
                        VaultBrowserWidget::setShowBrowserHintSetting,
                        DEFAULT_SHOW_BROWSER_HINT
                ),
                HudSetting.toggle("alwaysShowVaultNumber", "Always show vault number",
                        () -> true,
                        VaultBrowserWidget::getAlwaysShowVaultNumberSetting,
                        VaultBrowserWidget::setAlwaysShowVaultNumberSetting,
                        DEFAULT_ALWAYS_SHOW_VAULT_NUMBER
                ),
                HudSetting.slider(
                        "scale", "Vault panel scale",
                        0.6f, 2f, 0.1f,
                        () -> true,
                        VaultBrowserWidget::getScaleSetting,
                        v -> VaultBrowserWidget.setScaleSetting((float) v),
                        DEFAULT_SCALE
                ),
                HudSetting.section("Wardrobe"),
                HudSetting.slider(
                        WARDROBE_SCALE_KEY, "Wardrobe UI scale",
                        0.6f, 1.5f, 0.1f,
                        () -> true,
                        VaultBrowserWidget::getWardrobeScaleSetting,
                        v -> VaultBrowserWidget.setWardrobeScaleSetting((float) v),
                        DEFAULT_WARDROBE_SCALE
                )
        );
    }
}
