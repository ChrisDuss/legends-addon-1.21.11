package legends.ultra.cool.addons.hud;

import net.minecraft.client.gui.DrawContext;

import java.util.List;

public abstract class HudWidget {
    public double x;
    public double y;
    public boolean enabled = false;
    protected final String name;
    public HudWidget(String name, double x, double y) {
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        enabled = !enabled;
    }

    public abstract void render(DrawContext context);
    public abstract double getWidth();
    public abstract double getHeight();

    public boolean isMouseOver(double mouseX, double mouseY) {
        int ix = (int) x;
        int iy = (int) y;
        return mouseX >= ix && mouseX <= ix + getWidth()
                && mouseY >= iy && mouseY <= iy + getHeight();
    }

    public void move(double dx, double dy) {
        x += dx;
        y += dy;
    }

    public void onScreenSizeChanged(int oldWidth, int oldHeight, int newWidth, int newHeight) {}

    public boolean hasSettings() {
        return true;
    }

    /** Per-widget settings schema for the editor to render dynamically. */
    public List<HudSetting> getSettings() {
        return List.of(); // default: none
    }

    public record HudSetting(
            String key,
            String label,
            Type type,
            float min,
            float max,
            float step,
            java.util.function.BooleanSupplier enabled,
            java.util.function.BooleanSupplier getBool,
            java.util.function.Consumer<Boolean> setBool,
            java.util.function.IntSupplier getColor,
            java.util.function.IntConsumer setColor,
            java.util.function.DoubleSupplier getFloat,
            java.util.function.DoubleConsumer setFloat,
            boolean defaultBool,
            int defaultColor,
            float defaultFloat
    ) {
        public enum Type { TOGGLE, COLOR, SLIDER }

        public static HudSetting toggle(String key, String label,
                                        java.util.function.BooleanSupplier enabled,
                                        java.util.function.BooleanSupplier get,
                                        java.util.function.Consumer<Boolean> set,
                                        boolean def) {
            return new HudSetting(key, label, Type.TOGGLE, 0,0,0,
                    enabled, get, set,
                    ()->0, c->{}, ()->0, v->{}, def, 0, 0f);
        }

        public static HudSetting color(String key, String label,
                                       java.util.function.BooleanSupplier enabled,
                                       java.util.function.IntSupplier get,
                                       java.util.function.IntConsumer set,
                                       int def) {
            return new HudSetting(key, label, Type.COLOR, 0,0,0,
                    enabled, ()->false, b->{},
                    get, set,
                    ()->0, v->{}, false, def, 0f);
        }

        public static HudSetting slider(String key, String label,
                                        float min, float max, float step,
                                        java.util.function.BooleanSupplier enabled,
                                        java.util.function.DoubleSupplier get,
                                        java.util.function.DoubleConsumer set,
                                        float def) {
            return new HudSetting(key, label, Type.SLIDER, min,max,step,
                    enabled, ()->false, b->{},
                    ()->0, c->{},
                    get, set, false, 0, def);
        }
    }

    protected void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.drawVerticalLine(x, y, y + h, color);
        ctx.drawVerticalLine(x + w, y, y + h, color);
        ctx.drawHorizontalLine(x, x + w, y, color);
        ctx.drawHorizontalLine(x, x + w, y + h, color);
    }

}

