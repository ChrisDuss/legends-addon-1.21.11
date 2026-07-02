package legends.ultra.cool.addons.hud;

import legends.ultra.cool.addons.util.AddonServerGate;
import net.minecraft.client.gui.DrawContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
        return enabled && AddonServerGate.shouldRunOnCurrentServer();
    }

    public boolean isConfiguredEnabled() {
        return enabled;
    }

    public void toggle() {
        enabled = !enabled;
    }

    public abstract void render(DrawContext context);
    public abstract double getWidth();
    public abstract double getHeight();

    public double getVisualX() {
        return x;
    }

    public double getVisualY() {
        return y;
    }

    public double getVisualWidth() {
        return getWidth();
    }

    public double getVisualHeight() {
        return getHeight();
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        double left = getVisualX();
        double top = getVisualY();
        return mouseX >= left && mouseX <= left + getVisualWidth()
                && mouseY >= top && mouseY <= top + getVisualHeight();
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
            float defaultFloat,
            String defaultString,
            java.util.function.Supplier<String> getString,
            java.util.function.Consumer<String> setString,
            List<HudOption> options,
            CustomListSpec customList
    ) {
        public enum Type { SECTION, TOGGLE, COLOR, SLIDER, DROPDOWN, CUSTOM_LIST }

        public boolean storesValue() {
            return type != Type.SECTION;
        }

        public static HudSetting section(String label) {
            return new HudSetting("__section__" + label, label, Type.SECTION, 0,0,0,
                    ()->false, ()->false, b->{},
                    ()->0, c->{}, ()->0, v->{}, false, 0, 0f,
                    "", () -> "", s -> {}, List.of(), null);
        }

        public static HudSetting toggle(String key, String label,
                                        java.util.function.BooleanSupplier enabled,
                                        java.util.function.BooleanSupplier get,
                                        java.util.function.Consumer<Boolean> set,
                                        boolean def) {
            return new HudSetting(key, label, Type.TOGGLE, 0,0,0,
                    enabled, get, set,
                    ()->0, c->{}, ()->0, v->{}, def, 0, 0f,
                    "", () -> "", s -> {}, List.of(), null);
        }

        public static HudSetting color(String key, String label,
                                       java.util.function.BooleanSupplier enabled,
                                       java.util.function.IntSupplier get,
                                       java.util.function.IntConsumer set,
                                       int def) {
            return new HudSetting(key, label, Type.COLOR, 0,0,0,
                    enabled, ()->false, b->{},
                    get, set,
                    ()->0, v->{}, false, def, 0f,
                    "", () -> "", s -> {}, List.of(), null);
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
                    get, set, false, 0, def,
                    "", () -> "", s -> {}, List.of(), null);
        }

        public static HudSetting dropdown(String key, String label,
                                          java.util.function.BooleanSupplier enabled,
                                          java.util.function.Supplier<String> get,
                                          java.util.function.Consumer<String> set,
                                          String def,
                                          List<HudOption> options) {
            String defaultValue = def == null ? "" : def;
            List<HudOption> safeOptions = options == null ? List.of() : List.copyOf(options);
            return new HudSetting(key, label, Type.DROPDOWN, 0,0,0,
                    enabled, ()->false, b->{},
                    ()->0, c->{},
                    ()->0, v->{}, false, 0, 0f,
                    defaultValue, get, set, safeOptions, null);
        }

        public static HudSetting customList(String key, String label,
                                            java.util.function.BooleanSupplier enabled,
                                            CustomListSpec customList) {
            return new HudSetting(key, label, Type.CUSTOM_LIST, 0,0,0,
                    enabled, ()->false, b->{},
                    ()->0, c->{},
                    ()->0, v->{}, false, 0, 0f,
                    "", () -> "", s -> {}, List.of(), customList);
        }
    }

    public record HudOption(String value, String label) {
        public static HudOption of(String value, String label) {
            return new HudOption(value, label);
        }
    }

    public record CustomListSpec(
            List<CustomField> fields,
            Supplier<List<CustomEntry>> getEntries,
            Consumer<List<CustomEntry>> setEntries,
            Function<CustomEntry, String> title,
            Function<CustomEntry, String> description
    ) {
        public CustomListSpec {
            fields = List.copyOf(fields);
        }
    }

    public record CustomField(
            String key,
            String label,
            String placeholder,
            InputType inputType,
            boolean required,
            int maxLength
    ) {
        public enum InputType {
            TEXT,
            POSITIVE_NUMBER
        }

        public static CustomField text(String key, String label, String placeholder, boolean required, int maxLength) {
            return new CustomField(key, label, placeholder, InputType.TEXT, required, maxLength);
        }

        public static CustomField positiveNumber(String key, String label, String placeholder, boolean required) {
            return new CustomField(key, label, placeholder, InputType.POSITIVE_NUMBER, required, 24);
        }
    }

    public record CustomEntry(Map<String, String> values) {
        public CustomEntry {
            values = Map.copyOf(new LinkedHashMap<>(values));
        }

        public String get(String key) {
            return values.getOrDefault(key, "");
        }
    }

    protected void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.drawVerticalLine(x, y, y + h, color);
        ctx.drawVerticalLine(x + w, y, y + h, color);
        ctx.drawHorizontalLine(x, x + w, y, color);
        ctx.drawHorizontalLine(x, x + w, y + h, color);
    }

}

