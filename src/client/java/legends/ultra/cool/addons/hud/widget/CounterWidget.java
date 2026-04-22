package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class CounterWidget extends HudWidget {

    private int ticksElapsed = 0;
    private int value = 0;
    private final int ticksPerIncrement = 20;

    public CounterWidget(int x, int y) {
        super("Counter", x, y);
    }

    public void tick() {
        ticksElapsed++;
        if (ticksElapsed >= ticksPerIncrement) {
            value++;
            ticksElapsed = 0;
        }
    }

    public void reset() {
        ticksElapsed = 0;
        value = 0;
    }

    public int getValue() {
        return value;
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

        String text = "Time: " + value;
        int width = client.textRenderer.getWidth(text);
        int height = client.textRenderer.fontHeight;

        if (bgToggle) {
            context.fill((int) (x - 3), (int) (y - 3), (int) (x + width + 2), (int) (y + height + 2), bgColor);
        }

        if (brdToggle) {
            drawBorder(context, (int) (x - 3), (int) (y - 3), width + 5, height + 5, brdColor);
        }

        context.drawText(client.textRenderer, text, (int) x, (int) y, textColor, false);
    }

    @Override
    public double getWidth() {
        return MinecraftClient.getInstance().textRenderer.getWidth("Time: " + value);
    }

    @Override
    public double getHeight() {
        return MinecraftClient.getInstance().textRenderer.fontHeight;
    }

    @Override
    public List<HudSetting> getSettings() {
        final String w = this.getName();

        return List.of(
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
                )
        );
    }


}
