package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudWidget;
import net.minecraft.client.gui.DrawContext;

public class Bar extends HudWidget {
    private final String ownerId;
    private double min;
    private double max;

    public Bar(String ownerId, String name, double x, double y, double min, double max) {
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

        double width = getWidth();
        double height = getHeight();
        double healthWidth = 0;
        if (max > 0) {
            healthWidth = width * (float)min / (float) max;
        }

        context.fill((int) (x - 3), (int) (y - 3), (int) (x + width + 2), (int) (y + height + 2), 0x50000000);
        context.fill((int) (x - 3), (int) (y - 3), (int) (x + healthWidth + 2), (int) (y + height + 2), 0xFFFC5454);
        drawBorder(context, (int) (x - 3), (int) (y - 3), (int) width + 5, (int) height + 5, 0xFFFFFFFF);
    }

    @Override
    public double getWidth() {
        return WidgetConfigManager.getInt(ownerId, "barWidth", 80);
    }

    @Override
    public double getHeight() {
        return 3;
    }

    public void setMin(double min) {
        if (min >= 0) {
            this.min = min;
        }
    }

    public void setMax(double max) {
        if (max > 0) {
            this.max = max;
        }
    }
}
