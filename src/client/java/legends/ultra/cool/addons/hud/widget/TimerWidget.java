package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class TimerWidget extends HudWidget {

    private int ticksElapsed = 0;
    private double value = 0;
    private boolean toggled = false;

    public TimerWidget(int x, int y) {
        super("Timer", x, y);
    }

    public void tick(boolean running) {
        if (!running) return;

        ticksElapsed++;
        if (ticksElapsed >= 1) {
            value += 0.05;
            ticksElapsed = 0;
        }
    }

    public void toggleTick() {
        toggled = !toggled;
    }

    public boolean getToggleState() {
        return toggled;
    }

    public void reset() {
        ticksElapsed = 0;
        value = 0;
    }

    @Override
    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();

        final String w = getName();
        boolean bgToggle = WidgetConfigManager.getBool(w, "bgToggle", true);
        int bgColor = WidgetConfigManager.getInt(w, "bgColor", 0x80000000);
        boolean brdToggle = WidgetConfigManager.getBool(w, "brdToggle", true);
        int brdColor = WidgetConfigManager.getInt(w, "brdColor", 0xFFFFFFFF);
        int textColor = WidgetConfigManager.getInt(w, "textColor", 0xFFFFFFFF);
        TextAlignment textAlignment = TextAlignment.fromId(
                WidgetConfigManager.getString(w, TextAlignment.SETTING_KEY, TextAlignment.LEFT_DEFAULT_ID)
        );

        String text = timerText();
        int width = client.textRenderer.getWidth(text) + 1;
        int height = client.textRenderer.fontHeight;
        double textX = textAlignment.leftX(x, width);

        if (bgToggle) {
            context.fill((int) (textX - 3), (int) (y - 3), (int) (textX + width + 2), (int) (y + height + 2), bgColor);
        }

        if (brdToggle) {
            drawBorder(context, (int) (textX - 3), (int) (y - 3), width + 5, height + 5, brdColor);
        }

        context.drawText(client.textRenderer, text, (int) textX + 1, (int) y + 1, textColor, !bgToggle);
    }

    @Override
    public double getWidth() {
        return MinecraftClient.getInstance().textRenderer.getWidth(timerText()) + 1;
    }

    @Override
    public double getHeight() {
        return MinecraftClient.getInstance().textRenderer.fontHeight;
    }

    @Override
    public double getVisualX() {
        double textX = currentTextAlignment().leftX(x, getWidth());
        return usesDecoratedBounds() ? textX - 3 : textX;
    }

    @Override
    public double getVisualY() {
        return usesDecoratedBounds() ? y - 3 : y;
    }

    @Override
    public double getVisualWidth() {
        return getWidth() + (usesDecoratedBounds() ? 5 : 0);
    }

    @Override
    public double getVisualHeight() {
        return getHeight() + (usesDecoratedBounds() ? 5 : 0);
    }

    private boolean usesDecoratedBounds() {
        return WidgetConfigManager.getBool(getName(), "bgToggle", true)
                || WidgetConfigManager.getBool(getName(), "brdToggle", true);
    }

    private TextAlignment currentTextAlignment() {
        return TextAlignment.fromId(
                WidgetConfigManager.getString(getName(), TextAlignment.SETTING_KEY, TextAlignment.LEFT_DEFAULT_ID)
        );
    }

    private String timerText() {
        return "Stopwatch: " + String.format("%.2f", value);
    }

    @Override
    public List<HudSetting> getSettings() {
        final String w = this.getName();

        return List.of(
                HudSetting.section("Style"),
                HudSetting.toggle("bgToggle", "Background",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, "bgToggle", true),
                        b -> WidgetConfigManager.setBool(w, "bgToggle", b, true),
                        true
                ),
                HudSetting.color("bgColor", "BG Color",
                        () -> WidgetConfigManager.getBool(w, "bgToggle", true),
                        () -> WidgetConfigManager.getInt(w, "bgColor", 0x80000000),
                        c -> WidgetConfigManager.setInt(w, "bgColor", c, true),
                        0x80000000
                ),

                HudSetting.toggle("brdToggle", "Border",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, "brdToggle", true),
                        b -> WidgetConfigManager.setBool(w, "brdToggle", b, true),
                        true
                ),
                HudSetting.color("brdColor", "Border Color",
                        () -> WidgetConfigManager.getBool(w, "brdToggle", true),
                        () -> WidgetConfigManager.getInt(w, "brdColor", 0xFFFFFFFF),
                        c -> WidgetConfigManager.setInt(w, "brdColor", c, true),
                        0xFFFFFFFF
                ),

                HudSetting.color("textColor", "Text Color",
                        () -> true,
                        () -> WidgetConfigManager.getInt(w, "textColor", 0xFFFFFFFF),
                        c -> WidgetConfigManager.setInt(w, "textColor", c, true),
                        0xFFFFFFFF
                ),
                HudSetting.section("Alignment"),
                HudSetting.dropdown(TextAlignment.SETTING_KEY, "Text Align",
                        () -> true,
                        () -> WidgetConfigManager.getString(w, TextAlignment.SETTING_KEY, TextAlignment.LEFT_DEFAULT_ID),
                        value -> TextAlignment.setForWidgetPreservingLeft(this, value, TextAlignment.LEFT_DEFAULT_ID),
                        TextAlignment.LEFT_DEFAULT_ID,
                        TextAlignment.options()
                )
        );
    }
}
