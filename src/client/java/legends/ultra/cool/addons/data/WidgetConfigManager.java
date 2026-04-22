package legends.ultra.cool.addons.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import legends.ultra.cool.addons.hud.HudWidget;
import net.minecraft.client.MinecraftClient;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class WidgetConfigManager {

    private WidgetConfigManager() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "legendsaddon_widgets.json";

    private static final Type MAP_TYPE = new TypeToken<Map<String, WidgetData>>() {}.getType();

    private static final String HORIZONTAL_ANCHOR_LEFT = "left";
    private static final String HORIZONTAL_ANCHOR_CENTER = "center";
    private static final String HORIZONTAL_ANCHOR_RIGHT = "right";
    private static final String VERTICAL_ANCHOR_TOP = "top";
    private static final String VERTICAL_ANCHOR_CENTER = "center";
    private static final String VERTICAL_ANCHOR_BOTTOM = "bottom";

    private static Map<String, WidgetData> widgetDataMap = new HashMap<>();
    private static boolean loaded = false;

    private static Path getConfigPath() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("config/" + FILE_NAME);
    }

    public static void load() {
        if (loaded) return;
        loaded = true;

        Path path = getConfigPath();
        if (!Files.exists(path)) {
            widgetDataMap = new HashMap<>();
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, WidgetData> loadedMap = GSON.fromJson(reader, MAP_TYPE);
            widgetDataMap = (loadedMap != null) ? loadedMap : new HashMap<>();
        } catch (Exception e) {
            e.printStackTrace();
            widgetDataMap = new HashMap<>();
        }
    }

    public static void save() {
        Path path = getConfigPath();
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(widgetDataMap, MAP_TYPE, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean configExists() {
        return Files.exists(getConfigPath());
    }

    public static void registerWidget(HudWidget widget) {
        if (widget == null) return;
        load();

        String id = widget.getName();
        WidgetData data = widgetDataMap.get(id);

        if (data == null) {
            data = new WidgetData();
            seedInitialPosition(data, widget.x, widget.y);
            data.enabled = widget.enabled;

            for (HudWidget.HudSetting s : safeSettings(widget)) {
                seedDefaultSettingIfMissing(data, s);
            }

            widgetDataMap.put(id, data);
            save();
            return;
        }

        migrateWidgetDataIfNeeded(data);
        applyWidgetPosition(widget);
        widget.enabled = data.enabled;

        boolean changed = false;
        for (HudWidget.HudSetting s : safeSettings(widget)) {
            if (!data.settings.containsKey(s.key())) {
                seedDefaultSettingIfMissing(data, s);
                changed = true;
            }
        }

        if (changed) {
            save();
        }
    }

    public static void applyWidgetPosition(HudWidget widget) {
        if (widget == null) return;
        load();
        if (!hasUsableWindowSize()) {
            return;
        }

        WidgetData data = widgetDataMap.get(widget.getName());
        if (data == null) {
            return;
        }

        migrateWidgetDataIfNeeded(data);
        double anchoredX = resolveAnchoredX(data.horizontalAnchor, data.offsetX);
        double anchoredY = resolveAnchoredY(data.verticalAnchor, data.offsetY);

        if (isOffscreen(widget, anchoredX, anchoredY) && isOnscreen(widget, data.x, data.y)) {
            writeAnchoredPosition(data, data.x, data.y);
            anchoredX = resolveAnchoredX(data.horizontalAnchor, data.offsetX);
            anchoredY = resolveAnchoredY(data.verticalAnchor, data.offsetY);
            save();
        }

        widget.x = anchoredX;
        widget.y = anchoredY;
    }

    public static void updateWidget(HudWidget widget) {
        if (widget == null) return;
        load();

        String id = widget.getName();
        WidgetData data = widgetDataMap.get(id);
        if (data == null) {
            registerWidget(widget);
            data = widgetDataMap.get(id);
            if (data == null) return;
        }

        writeAnchoredPosition(data, widget.x, widget.y);
        data.enabled = widget.enabled;
        save();
    }

    public static void resetAll() {
        load();
        widgetDataMap.clear();
        save();
    }

    public static void clearSetting(String widgetId, String key, boolean autosave) {
        load();
        WidgetData d = widgetDataMap.get(widgetId);
        if (d == null) return;
        if (d.settings.remove(key) != null && autosave) save();
    }

    public static boolean getBool(String widgetId, String key, boolean def) {
        load();
        WidgetData d = widgetDataMap.get(widgetId);
        if (d == null) return def;
        JsonElement e = d.settings.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;

        JsonPrimitive p = e.getAsJsonPrimitive();
        try {
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsInt() != 0;
            if (p.isString()) return Boolean.parseBoolean(p.getAsString());
        } catch (Exception ignored) {}
        return def;
    }

    public static int getInt(String widgetId, String key, int def) {
        load();
        WidgetData d = widgetDataMap.get(widgetId);
        if (d == null) return def;
        JsonElement e = d.settings.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;

        JsonPrimitive p = e.getAsJsonPrimitive();
        try {
            if (p.isNumber()) return p.getAsInt();
            if (p.isString()) return Integer.parseInt(p.getAsString());
            if (p.isBoolean()) return p.getAsBoolean() ? 1 : 0;
        } catch (Exception ignored) {}
        return def;
    }

    public static float getFloat(String widgetId, String key, float def) {
        load();
        WidgetData d = widgetDataMap.get(widgetId);
        if (d == null) return def;
        JsonElement e = d.settings.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;

        JsonPrimitive p = e.getAsJsonPrimitive();
        try {
            if (p.isNumber()) return p.getAsFloat();
            if (p.isString()) return Float.parseFloat(p.getAsString());
            if (p.isBoolean()) return p.getAsBoolean() ? 1f : 0f;
        } catch (Exception ignored) {}
        return def;
    }

    public static String getString(String widgetId, String key, String def) {
        load();
        WidgetData d = widgetDataMap.get(widgetId);
        if (d == null) return def;
        JsonElement e = d.settings.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;

        try {
            return e.getAsString();
        } catch (Exception ignored) {}
        return def;
    }

    public static void setBool(String widgetId, String key, boolean value, boolean autosave) {
        load();
        WidgetData d = ensure(widgetId);
        d.settings.put(key, new JsonPrimitive(value));
        if (autosave) save();
    }

    public static void setInt(String widgetId, String key, int value, boolean autosave) {
        load();
        WidgetData d = ensure(widgetId);
        d.settings.put(key, new JsonPrimitive(value));
        if (autosave) save();
    }

    public static void setFloat(String widgetId, String key, float value, boolean autosave) {
        load();
        WidgetData d = ensure(widgetId);
        d.settings.put(key, new JsonPrimitive(value));
        if (autosave) save();
    }

    public static void setString(String widgetId, String key, String value, boolean autosave) {
        load();
        WidgetData d = ensure(widgetId);
        d.settings.put(key, new JsonPrimitive(value));
        if (autosave) save();
    }

    public static float getAnchoredXSetting(String widgetId, String key, float defaultAbsolute) {
        load();
        WidgetData d = widgetDataMap.get(widgetId);
        if (d == null) {
            return defaultAbsolute;
        }

        AnchorPosition position = readSettingAnchorPosition(d, key, defaultAbsolute, 0f);
        return (float) resolveAnchoredX(position.horizontalAnchor(), position.offsetX());
    }

    public static float getAnchoredYSetting(String widgetId, String key, float defaultAbsolute) {
        load();
        WidgetData d = widgetDataMap.get(widgetId);
        if (d == null) {
            return defaultAbsolute;
        }

        AnchorPosition position = readSettingAnchorPosition(d, key, 0f, defaultAbsolute);
        return (float) resolveAnchoredY(position.verticalAnchor(), position.offsetY());
    }

    public static void setAnchoredXSetting(String widgetId, String key, float absoluteValue, boolean autosave) {
        load();
        WidgetData d = ensure(widgetId);
        String anchor = closestHorizontalAnchor(absoluteValue);
        d.settings.put(key + "_anchor_x", new JsonPrimitive(anchor));
        d.settings.put(key + "_offset_x", new JsonPrimitive(absoluteValue - horizontalAnchorCoordinate(anchor)));
        d.settings.remove(key);
        d.settings.remove(key + "_relative");
        if (autosave) save();
    }

    public static void setAnchoredYSetting(String widgetId, String key, float absoluteValue, boolean autosave) {
        load();
        WidgetData d = ensure(widgetId);
        String anchor = closestVerticalAnchor(absoluteValue);
        d.settings.put(key + "_anchor_y", new JsonPrimitive(anchor));
        d.settings.put(key + "_offset_y", new JsonPrimitive(absoluteValue - verticalAnchorCoordinate(anchor)));
        d.settings.remove(key);
        d.settings.remove(key + "_relative");
        if (autosave) save();
    }

    private static WidgetData ensure(String widgetId) {
        WidgetData d = widgetDataMap.get(widgetId);
        if (d == null) {
            d = new WidgetData();
            widgetDataMap.put(widgetId, d);
        }
        return d;
    }

    private static void migrateWidgetDataIfNeeded(WidgetData data) {
        if (data.horizontalAnchor != null && data.verticalAnchor != null) {
            return;
        }

        double absoluteX = data.relativePosition ? data.x * getScaledWidth() : data.x;
        double absoluteY = data.relativePosition ? data.y * getScaledHeight() : data.y;
        writeAnchoredPosition(data, absoluteX, absoluteY);
    }

    private static void writeAnchoredPosition(WidgetData data, double absoluteX, double absoluteY) {
        String horizontalAnchor = closestHorizontalAnchor(absoluteX);
        String verticalAnchor = closestVerticalAnchor(absoluteY);
        data.horizontalAnchor = horizontalAnchor;
        data.verticalAnchor = verticalAnchor;
        data.offsetX = absoluteX - horizontalAnchorCoordinate(horizontalAnchor);
        data.offsetY = absoluteY - verticalAnchorCoordinate(verticalAnchor);
        data.x = absoluteX;
        data.y = absoluteY;
        data.relativePosition = false;
    }

    private static void seedInitialPosition(WidgetData data, double absoluteX, double absoluteY) {
        if (!hasUsableWindowSize()) {
            data.horizontalAnchor = HORIZONTAL_ANCHOR_LEFT;
            data.verticalAnchor = VERTICAL_ANCHOR_TOP;
            data.offsetX = absoluteX;
            data.offsetY = absoluteY;
            data.x = absoluteX;
            data.y = absoluteY;
            data.relativePosition = false;
            return;
        }

        writeAnchoredPosition(data, absoluteX, absoluteY);
    }

    private static AnchorPosition readSettingAnchorPosition(WidgetData data, String key, float defaultX, float defaultY) {
        JsonElement anchorX = data.settings.get(key + "_anchor_x");
        JsonElement anchorY = data.settings.get(key + "_anchor_y");
        JsonElement offsetX = data.settings.get(key + "_offset_x");
        JsonElement offsetY = data.settings.get(key + "_offset_y");

        if (anchorX != null && anchorY != null && offsetX != null && offsetY != null) {
            try {
                return new AnchorPosition(anchorX.getAsString(), anchorY.getAsString(), offsetX.getAsDouble(), offsetY.getAsDouble());
            } catch (Exception ignored) {}
        }

        float absoluteX = migrateLegacySettingX(data, key, defaultX);
        float absoluteY = migrateLegacySettingY(data, key, defaultY);
        String horizontalAnchor = closestHorizontalAnchor(absoluteX);
        String verticalAnchor = closestVerticalAnchor(absoluteY);
        AnchorPosition migrated = new AnchorPosition(
                horizontalAnchor,
                verticalAnchor,
                absoluteX - horizontalAnchorCoordinate(horizontalAnchor),
                absoluteY - verticalAnchorCoordinate(verticalAnchor)
        );
        data.settings.put(key + "_anchor_x", new JsonPrimitive(migrated.horizontalAnchor()));
        data.settings.put(key + "_anchor_y", new JsonPrimitive(migrated.verticalAnchor()));
        data.settings.put(key + "_offset_x", new JsonPrimitive(migrated.offsetX()));
        data.settings.put(key + "_offset_y", new JsonPrimitive(migrated.offsetY()));
        save();
        return migrated;
    }

    private static float migrateLegacySettingX(WidgetData data, String key, float defaultAbsolute) {
        JsonElement relative = data.settings.get(key + "_relative");
        if (relative != null && relative.isJsonPrimitive()) {
            try {
                return (float) (relative.getAsDouble() * getScaledWidth());
            } catch (Exception ignored) {}
        }

        JsonElement absolute = data.settings.get(key);
        if (absolute != null && absolute.isJsonPrimitive()) {
            try {
                return absolute.getAsFloat();
            } catch (Exception ignored) {}
        }

        return defaultAbsolute;
    }

    private static float migrateLegacySettingY(WidgetData data, String key, float defaultAbsolute) {
        JsonElement relative = data.settings.get(key + "_relative");
        if (relative != null && relative.isJsonPrimitive()) {
            try {
                return (float) (relative.getAsDouble() * getScaledHeight());
            } catch (Exception ignored) {}
        }

        JsonElement absolute = data.settings.get(key);
        if (absolute != null && absolute.isJsonPrimitive()) {
            try {
                return absolute.getAsFloat();
            } catch (Exception ignored) {}
        }

        return defaultAbsolute;
    }

    private static double resolveAnchoredX(String anchor, double offset) {
        return horizontalAnchorCoordinate(anchor) + offset;
    }

    private static double resolveAnchoredY(String anchor, double offset) {
        return verticalAnchorCoordinate(anchor) + offset;
    }

    private static String closestHorizontalAnchor(double absoluteX) {
        double left = Math.abs(absoluteX);
        double center = Math.abs(absoluteX - getScaledWidth() / 2d);
        double right = Math.abs(absoluteX - getScaledWidth());

        if (center <= left && center <= right) return HORIZONTAL_ANCHOR_CENTER;
        return right < left ? HORIZONTAL_ANCHOR_RIGHT : HORIZONTAL_ANCHOR_LEFT;
    }

    private static String closestVerticalAnchor(double absoluteY) {
        double top = Math.abs(absoluteY);
        double center = Math.abs(absoluteY - getScaledHeight() / 2d);
        double bottom = Math.abs(absoluteY - getScaledHeight());

        if (center <= top && center <= bottom) return VERTICAL_ANCHOR_CENTER;
        return bottom < top ? VERTICAL_ANCHOR_BOTTOM : VERTICAL_ANCHOR_TOP;
    }

    private static double horizontalAnchorCoordinate(String anchor) {
        return switch (anchor) {
            case HORIZONTAL_ANCHOR_CENTER -> getScaledWidth() / 2d;
            case HORIZONTAL_ANCHOR_RIGHT -> getScaledWidth();
            default -> 0d;
        };
    }

    private static double verticalAnchorCoordinate(String anchor) {
        return switch (anchor) {
            case VERTICAL_ANCHOR_CENTER -> getScaledHeight() / 2d;
            case VERTICAL_ANCHOR_BOTTOM -> getScaledHeight();
            default -> 0d;
        };
    }

    private static double getScaledWidth() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return 1d;
        }
        return Math.max(1, client.getWindow().getScaledWidth());
    }

    private static double getScaledHeight() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return 1d;
        }
        return Math.max(1, client.getWindow().getScaledHeight());
    }

    private static boolean hasUsableWindowSize() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null
                && client.getWindow() != null
                && client.getWindow().getScaledWidth() > 1
                && client.getWindow().getScaledHeight() > 1;
    }

    private static boolean isOffscreen(HudWidget widget, double x, double y) {
        double width = Math.max(1d, widget.getWidth());
        double height = Math.max(1d, widget.getHeight());
        double screenWidth = getScaledWidth();
        double screenHeight = getScaledHeight();
        return x + width < 0 || y + height < 0 || x > screenWidth || y > screenHeight;
    }

    private static boolean isOnscreen(HudWidget widget, double x, double y) {
        return !isOffscreen(widget, x, y);
    }

    private static Iterable<HudWidget.HudSetting> safeSettings(HudWidget w) {
        var s = w.getSettings();
        return (s != null) ? s : java.util.List.of();
    }

    private static void seedDefaultSettingIfMissing(WidgetData data, HudWidget.HudSetting s) {
        switch (s.type()) {
            case TOGGLE -> data.settings.put(s.key(), new JsonPrimitive(s.defaultBool()));
            case COLOR -> data.settings.put(s.key(), new JsonPrimitive(s.defaultColor()));
            case SLIDER -> data.settings.put(s.key(), new JsonPrimitive(s.defaultFloat()));
        }
    }

    private record AnchorPosition(String horizontalAnchor, String verticalAnchor, double offsetX, double offsetY) {}

    public static final class WidgetData {
        public double x;
        public double y;
        public boolean enabled;
        public boolean relativePosition;
        public String horizontalAnchor;
        public String verticalAnchor;
        public double offsetX;
        public double offsetY;
        public Map<String, JsonElement> settings = new HashMap<>();
    }
}
