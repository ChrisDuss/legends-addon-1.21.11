package legends.ultra.cool.addons.hud.widget.otherTypes;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudWidget;
import legends.ultra.cool.addons.util.AddonServerGate;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public final class ContainerValueWidget extends HudWidget {
    public static final String WIDGET_NAME = "Container Value";
    public static final String TEXT_ALIGN_KEY = "textAlignment";
    public static final String TEXT_ALIGN_LEFT = "left";
    public static final String TEXT_ALIGN_CENTER = "center";
    public static final String TEXT_ALIGN_RIGHT = "right";
    private static final String INCLUDE_PLAYER_INVENTORY_KEY = "includePlayerInventory";
    private static final String SHOW_STACK_TOOLTIP_TOTAL_KEY = "showStackTooltipTotal";
    private static final String DRAG_LABEL_KEY = "dragLabel";
    private static final String BG_TOGGLE_KEY = "bgToggle";
    private static final String BG_COLOR_KEY = "bgColor";
    private static final String BORDER_TOGGLE_KEY = "brdToggle";
    private static final String BORDER_COLOR_KEY = "brdColor";
    private static final String TEXT_COLOR_KEY = "textColor";
    private static final boolean DEFAULT_INCLUDE_PLAYER_INVENTORY = false;
    private static final boolean DEFAULT_SHOW_STACK_TOOLTIP_TOTAL = true;
    private static final boolean DEFAULT_DRAG_LABEL = false;
    private static final boolean DEFAULT_BG_TOGGLE = true;
    private static final boolean DEFAULT_BORDER_TOGGLE = true;
    private static final int DEFAULT_BG_COLOR = 0xB0000000;
    private static final int DEFAULT_BORDER_COLOR = 0x99FCE75B;
    private static final int DEFAULT_TEXT_COLOR = 0xFFFCE75B;

    private static ContainerValueWidget INSTANCE;

    public ContainerValueWidget() {
        super(WIDGET_NAME, 0, 0);
        this.enabled = true;
        INSTANCE = this;
    }

    public static boolean isEnabledGlobal() {
        return AddonServerGate.shouldRunOnCurrentServer() && INSTANCE != null && INSTANCE.isEnabled();
    }

    public static boolean shouldShowStackTooltipTotal() {
        return isEnabledGlobal() && WidgetConfigManager.getBool(
                WIDGET_NAME,
                SHOW_STACK_TOOLTIP_TOTAL_KEY,
                DEFAULT_SHOW_STACK_TOOLTIP_TOTAL
        );
    }

    public static boolean shouldDragLabel() {
        return isEnabledGlobal() && WidgetConfigManager.getBool(
                WIDGET_NAME,
                DRAG_LABEL_KEY,
                DEFAULT_DRAG_LABEL
        );
    }

    public static boolean shouldShowBackground() {
        return WidgetConfigManager.getBool(WIDGET_NAME, BG_TOGGLE_KEY, DEFAULT_BG_TOGGLE);
    }

    public static int backgroundColor() {
        return WidgetConfigManager.getInt(WIDGET_NAME, BG_COLOR_KEY, DEFAULT_BG_COLOR);
    }

    public static boolean shouldShowBorder() {
        return WidgetConfigManager.getBool(WIDGET_NAME, BORDER_TOGGLE_KEY, DEFAULT_BORDER_TOGGLE);
    }

    public static int borderColor() {
        return WidgetConfigManager.getInt(WIDGET_NAME, BORDER_COLOR_KEY, DEFAULT_BORDER_COLOR);
    }

    public static int textColor() {
        return WidgetConfigManager.getInt(WIDGET_NAME, TEXT_COLOR_KEY, DEFAULT_TEXT_COLOR);
    }

    public static String textAlignment() {
        String value = WidgetConfigManager.getString(WIDGET_NAME, TEXT_ALIGN_KEY, TEXT_ALIGN_LEFT);
        return switch (value) {
            case TEXT_ALIGN_CENTER, TEXT_ALIGN_RIGHT -> value;
            default -> TEXT_ALIGN_LEFT;
        };
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
                HudSetting.section("Style"),
                HudSetting.toggle(BG_TOGGLE_KEY, "Background",
                        () -> true,
                        ContainerValueWidget::shouldShowBackground,
                        value -> WidgetConfigManager.setBool(WIDGET_NAME, BG_TOGGLE_KEY, value, true),
                        DEFAULT_BG_TOGGLE
                ),
                HudSetting.color(BG_COLOR_KEY, "BG Color",
                        ContainerValueWidget::shouldShowBackground,
                        ContainerValueWidget::backgroundColor,
                        value -> WidgetConfigManager.setInt(WIDGET_NAME, BG_COLOR_KEY, value, true),
                        DEFAULT_BG_COLOR
                ),
                HudSetting.toggle(BORDER_TOGGLE_KEY, "Border",
                        () -> true,
                        ContainerValueWidget::shouldShowBorder,
                        value -> WidgetConfigManager.setBool(WIDGET_NAME, BORDER_TOGGLE_KEY, value, true),
                        DEFAULT_BORDER_TOGGLE
                ),
                HudSetting.color(BORDER_COLOR_KEY, "Border Color",
                        ContainerValueWidget::shouldShowBorder,
                        ContainerValueWidget::borderColor,
                        value -> WidgetConfigManager.setInt(WIDGET_NAME, BORDER_COLOR_KEY, value, true),
                        DEFAULT_BORDER_COLOR
                ),
                HudSetting.color(TEXT_COLOR_KEY, "Text Color",
                        () -> true,
                        ContainerValueWidget::textColor,
                        value -> WidgetConfigManager.setInt(WIDGET_NAME, TEXT_COLOR_KEY, value, true),
                        DEFAULT_TEXT_COLOR
                ),
                HudSetting.section("Alignment"),
                HudSetting.dropdown(TEXT_ALIGN_KEY, "Text Align",
                        () -> true,
                        ContainerValueWidget::textAlignment,
                        value -> WidgetConfigManager.setString(WIDGET_NAME, TEXT_ALIGN_KEY, value, true),
                        TEXT_ALIGN_LEFT,
                        List.of(
                                HudOption.of(TEXT_ALIGN_LEFT, "Left"),
                                HudOption.of(TEXT_ALIGN_CENTER, "Center"),
                                HudOption.of(TEXT_ALIGN_RIGHT, "Right")
                        )
                ),
                HudSetting.section("Other"),
                HudSetting.toggle(SHOW_STACK_TOOLTIP_TOTAL_KEY, "Stack tooltip total",
                        () -> true,
                        () -> WidgetConfigManager.getBool(
                                WIDGET_NAME,
                                SHOW_STACK_TOOLTIP_TOTAL_KEY,
                                DEFAULT_SHOW_STACK_TOOLTIP_TOTAL
                        ),
                        value -> WidgetConfigManager.setBool(WIDGET_NAME, SHOW_STACK_TOOLTIP_TOTAL_KEY, value, true),
                        DEFAULT_SHOW_STACK_TOOLTIP_TOTAL
                ),
                HudSetting.toggle(DRAG_LABEL_KEY, "Drag label",
                        () -> true,
                        ContainerValueWidget::shouldDragLabel,
                        value -> WidgetConfigManager.setBool(WIDGET_NAME, DRAG_LABEL_KEY, value, true),
                        DEFAULT_DRAG_LABEL
                )
        );
    }
}
