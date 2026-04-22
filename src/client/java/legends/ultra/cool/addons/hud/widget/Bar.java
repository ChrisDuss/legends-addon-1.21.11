package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudWidget;
import net.minecraft.client.gui.DrawContext;

public class Bar extends HudWidget {
    private final String ownerId;
    private int min;
    private int max;

    public Bar(String ownerId, String name, double x, double y, int min, int max) {
        super(name, x, y);
        this.ownerId = ownerId;
        this.min = min;
        this.max = max;
    }

    @Override
    public void render(DrawContext context) {
        if (!WidgetConfigManager.getBool(ownerId, "barToggle", false)) {
            return;
        }

        float width = getWidth();
        float height = getHeight();
        float healthWidth = 0;
        if (max > 0) {
            healthWidth = width * min / (float) max;
        }

        context.fill((int) (x - 3), (int) (y - 3), (int) (x + width + 2), (int) (y + height + 2), 0x50000000);
        context.fill((int) (x - 3), (int) (y - 3), (int) (x + healthWidth + 2), (int) (y + height + 2), 0xFFFC5454);
        drawBorder(context, (int) (x - 3), (int) (y - 3), (int) width + 5, (int) height + 5, 0xFFFFFFFF);
    }

    @Override
    public int getWidth() {
        return WidgetConfigManager.getInt(ownerId, "barWidth", 80);
    }

    @Override
    public int getHeight() {
        return 3;
    }

    public void setMin(int min) {
        if (min >= 0) {
            this.min = min;
        }
    }

    public void setMax(int max) {
        if (max > 0) {
            this.max = max;
        }
    }
}
