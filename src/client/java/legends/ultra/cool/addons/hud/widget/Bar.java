package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudWidget;
import net.minecraft.client.gui.DrawContext;

public class Bar extends HudWidget {
    private static final int LEFT_PADDING = 3;
    private static final int RIGHT_PADDING = 2;
    private static final int TOP_PADDING = 3;
    private static final int BOTTOM_PADDING = 2;

    private final String ownerId;
    private double min;
    private double max;
    private boolean rev;
    private int barColor = 0x00000000;

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

        int left = (int) Math.floor( (int) x - LEFT_PADDING);
        int top = (int) Math.floor( (int) y - TOP_PADDING);
        int right = (int) Math.ceil( (int) x + getWidth() + RIGHT_PADDING);
        int bottom = (int) Math.ceil( (int) y + BOTTOM_PADDING);

        rev = WidgetConfigManager.getBool(ownerId, "invertToggle", false);
        int totalWidth = Math.max(0, right - left);
        double ratio = max > 0 ? Math.max(0d, Math.min(1d, min / max)) : 0d;
        int fillWidth = (int) Math.round(totalWidth * ratio);
        int fillLeft = rev ? right - fillWidth : left;
        int fillRight = rev ? right : left + fillWidth;

        context.fill(left, top, right, bottom, 0x50000000);
        if (fillWidth > 0) {
            context.fill(fillLeft, top, fillRight, bottom, barColor);
        }
        drawBorder(context, left, top, totalWidth, bottom - top, 0xFFFFFFFF);
    }

    @Override
    public double getWidth() {
        return WidgetConfigManager.getInt(ownerId, "barWidth", 80);
    }

    @Override
    public double getHeight() {
        return 5;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x - LEFT_PADDING
                && mouseX <= x + getWidth() + RIGHT_PADDING
                && mouseY >= y - TOP_PADDING
                && mouseY <= y + BOTTOM_PADDING;
    }

    public void clampToScreen(int screenWidth, int screenHeight) {
        x = Math.max(LEFT_PADDING, Math.min(x, screenWidth - getWidth() - RIGHT_PADDING));
        y = Math.max(TOP_PADDING, Math.min(y, screenHeight - BOTTOM_PADDING));
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

    public void setBarColor(int color) {
        barColor = color;
    }
}
