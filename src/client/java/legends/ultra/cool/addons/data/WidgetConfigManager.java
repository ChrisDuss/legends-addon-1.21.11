package legends.ultra.cool.addons.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WidgetConfigManager {

    private WidgetConfigManager() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "legendsaddon_widgets.json";
    private static final String PROFILE_INDEX_FILE_NAME = "legendsaddon_widget_profiles.json";
    private static final String PROFILE_DIR_NAME = "widget_profiles";
    private static final String DEFAULT_PROFILE_ID = "default";
    private static final String DEFAULT_PROFILE_NAME = "Default";

    private static final Type MAP_TYPE = new TypeToken<Map<String, WidgetData>>() {}.getType();

    private static final String HORIZONTAL_ANCHOR_LEFT = "left";
    private static final String HORIZONTAL_ANCHOR_CENTER = "center";
    private static final String HORIZONTAL_ANCHOR_RIGHT = "right";
    private static final String VERTICAL_ANCHOR_TOP = "top";
    private static final String VERTICAL_ANCHOR_CENTER = "center";
    private static final String VERTICAL_ANCHOR_BOTTOM = "bottom";

    private static Map<String, WidgetData> widgetDataMap = new HashMap<>();
    private static List<WidgetProfile> profiles = new ArrayList<>();
    private static String activeProfileId = DEFAULT_PROFILE_ID;
    private static boolean loaded = false;
    private static boolean profileIndexLoaded = false;

    private static Path getConfigPath() {
        ensureProfileIndexLoaded();
        return getConfigPath(activeProfileId);
    }

    private static Path getConfigPath(String profileId) {
        if (DEFAULT_PROFILE_ID.equals(profileId)) {
            return AddonConfigPaths.configFile(FILE_NAME);
        }
        return AddonConfigPaths.configFile(PROFILE_DIR_NAME + "/" + profileId + ".json");
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

    public static List<WidgetProfile> getProfiles() {
        ensureProfileIndexLoaded();
        return List.copyOf(profiles);
    }

    public static String getActiveProfileId() {
        ensureProfileIndexLoaded();
        return activeProfileId;
    }

    public static String getActiveProfileName() {
        ensureProfileIndexLoaded();
        for (WidgetProfile profile : profiles) {
            if (profile.id.equals(activeProfileId)) {
                return profile.name;
            }
        }
        return DEFAULT_PROFILE_NAME;
    }

    public static boolean setActiveProfile(String profileId, List<HudWidget> widgets) {
        ensureProfileIndexLoaded();
        if (profileId == null || profileId.equals(activeProfileId) || profileById(profileId) == null) {
            return false;
        }

        save();
        activeProfileId = profileId;
        loaded = false;
        load();
        saveProfileIndex();
        applyActiveProfileToWidgets(widgets);
        return true;
    }

    public static WidgetProfile createProfileFromCurrent(List<HudWidget> widgets) {
        ensureProfileIndexLoaded();
        load();
        save();

        String name = nextProfileName();
        String id = uniqueProfileId(name);
        WidgetProfile profile = new WidgetProfile(id, name);
        profiles.add(profile);

        Map<String, WidgetData> copiedData = copyWidgetData(widgetDataMap);
        activeProfileId = id;
        widgetDataMap = copiedData;
        loaded = true;

        saveProfileIndex();
        save();
        applyActiveProfileToWidgets(widgets);
        return profile;
    }

    public static boolean renameProfile(String profileId, String newName) {
        ensureProfileIndexLoaded();
        if (profileId == null || newName == null) {
            return false;
        }

        String trimmedName = newName.trim();
        if (trimmedName.isBlank()) {
            return false;
        }
        if (trimmedName.length() > 32) {
            trimmedName = trimmedName.substring(0, 32).trim();
        }

        for (WidgetProfile profile : profiles) {
            if (!profile.id.equals(profileId) && profile.name.equalsIgnoreCase(trimmedName)) {
                return false;
            }
        }

        WidgetProfile profile = profileById(profileId);
        if (profile == null) {
            return false;
        }

        profile.name = trimmedName;
        saveProfileIndex();
        return true;
    }

    public static boolean deleteProfile(String profileId, List<HudWidget> widgets) {
        ensureProfileIndexLoaded();
        if (profileId == null || DEFAULT_PROFILE_ID.equals(profileId) || profiles.size() <= 1) {
            return false;
        }

        WidgetProfile profile = profileById(profileId);
        if (profile == null) {
            return false;
        }

        save();
        profiles.removeIf(candidate -> candidate.id.equals(profileId));
        boolean deletedActiveProfile = profileId.equals(activeProfileId);
        if (deletedActiveProfile) {
            activeProfileId = DEFAULT_PROFILE_ID;
            loaded = false;
            load();
        }
        saveProfileIndex();
        deleteProfileFile(profileId);

        if (deletedActiveProfile) {
            applyActiveProfileToWidgets(widgets);
        }
        return true;
    }

    public static void applyActiveProfileToWidgets(List<HudWidget> widgets) {
        if (widgets == null) {
            return;
        }

        load();
        for (HudWidget widget : widgets) {
            registerWidget(widget);
        }
    }

    public static void save() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

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
                if (!s.storesValue()) continue;
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
            if (!s.storesValue()) continue;
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

    public static <T> List<T> getObjectList(String widgetId, String key, Class<T> elementType) {
        load();
        WidgetData d = widgetDataMap.get(widgetId);
        if (d == null) return List.of();

        JsonElement element = d.settings.get(key);
        if (element == null || !element.isJsonArray()) return List.of();

        java.util.ArrayList<T> result = new java.util.ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            try {
                T value = GSON.fromJson(item, elementType);
                if (value != null) {
                    result.add(value);
                }
            } catch (Exception ignored) {}
        }
        return List.copyOf(result);
    }

    public static void setObjectList(String widgetId, String key, List<?> values, boolean autosave) {
        load();
        WidgetData d = ensure(widgetId);
        d.settings.put(key, GSON.toJsonTree(values == null ? List.of() : values));
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

    private static void ensureProfileIndexLoaded() {
        if (profileIndexLoaded) {
            return;
        }

        profileIndexLoaded = true;
        Path path = AddonConfigPaths.configFile(PROFILE_INDEX_FILE_NAME);
        ProfileIndex index = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                index = GSON.fromJson(reader, ProfileIndex.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        profiles = sanitizeProfiles(index == null ? null : index.profiles);
        activeProfileId = index == null ? DEFAULT_PROFILE_ID : index.activeProfileId;
        if (profileById(activeProfileId) == null) {
            activeProfileId = DEFAULT_PROFILE_ID;
        }

        if (index == null || index.profiles == null || profileById(DEFAULT_PROFILE_ID) == null) {
            saveProfileIndex();
        }
    }

    private static List<WidgetProfile> sanitizeProfiles(List<WidgetProfile> rawProfiles) {
        List<WidgetProfile> result = new ArrayList<>();
        result.add(new WidgetProfile(DEFAULT_PROFILE_ID, defaultProfileName(rawProfiles)));

        Set<String> seenIds = new HashSet<>();
        seenIds.add(DEFAULT_PROFILE_ID);

        if (rawProfiles == null) {
            return result;
        }

        for (WidgetProfile profile : rawProfiles) {
            if (profile == null || profile.id == null || profile.name == null) {
                continue;
            }

            String id = sanitizeProfileId(profile.id);
            String name = profile.name.trim();
            if (id.isBlank() || DEFAULT_PROFILE_ID.equals(id) || name.isBlank() || seenIds.contains(id)) {
                continue;
            }

            result.add(new WidgetProfile(id, name));
            seenIds.add(id);
        }

        return result;
    }

    private static String defaultProfileName(List<WidgetProfile> rawProfiles) {
        if (rawProfiles == null) {
            return DEFAULT_PROFILE_NAME;
        }

        for (WidgetProfile profile : rawProfiles) {
            if (profile != null && DEFAULT_PROFILE_ID.equals(profile.id) && profile.name != null && !profile.name.trim().isBlank()) {
                return profile.name.trim();
            }
        }

        return DEFAULT_PROFILE_NAME;
    }

    private static void saveProfileIndex() {
        Path path = AddonConfigPaths.configFile(PROFILE_INDEX_FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        ProfileIndex index = new ProfileIndex();
        index.activeProfileId = activeProfileId;
        index.profiles = new ArrayList<>(profiles);

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(index, ProfileIndex.class, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void deleteProfileFile(String profileId) {
        if (DEFAULT_PROFILE_ID.equals(profileId)) {
            return;
        }

        try {
            Files.deleteIfExists(getConfigPath(profileId));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static WidgetProfile profileById(String id) {
        for (WidgetProfile profile : profiles) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        return null;
    }

    private static String nextProfileName() {
        Set<String> names = new HashSet<>();
        for (WidgetProfile profile : profiles) {
            names.add(profile.name);
        }

        for (int index = 2; index < 1000; index++) {
            String candidate = "Profile " + index;
            if (!names.contains(candidate)) {
                return candidate;
            }
        }

        return "Profile " + (profiles.size() + 1);
    }

    private static String uniqueProfileId(String name) {
        String base = sanitizeProfileId(name);
        if (base.isBlank()) {
            base = "profile";
        }

        String candidate = base;
        int suffix = 2;
        while (profileById(candidate) != null) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static String sanitizeProfileId(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        boolean lastDash = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (allowed) {
                result.append(c);
                lastDash = false;
            } else if (!lastDash && result.length() > 0) {
                result.append('-');
                lastDash = true;
            }
        }

        while (result.length() > 0 && result.charAt(result.length() - 1) == '-') {
            result.deleteCharAt(result.length() - 1);
        }
        return result.toString();
    }

    private static Map<String, WidgetData> copyWidgetData(Map<String, WidgetData> source) {
        Map<String, WidgetData> copy = GSON.fromJson(GSON.toJson(source, MAP_TYPE), MAP_TYPE);
        return copy == null ? new HashMap<>() : copy;
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
            case SECTION -> {}
            case TOGGLE -> data.settings.put(s.key(), new JsonPrimitive(s.defaultBool()));
            case COLOR -> data.settings.put(s.key(), new JsonPrimitive(s.defaultColor()));
            case SLIDER -> data.settings.put(s.key(), new JsonPrimitive(s.defaultFloat()));
            case DROPDOWN -> data.settings.put(s.key(), new JsonPrimitive(s.defaultString()));
            case CUSTOM_LIST -> data.settings.put(s.key(), new JsonArray());
        }
    }

    private record AnchorPosition(String horizontalAnchor, String verticalAnchor, double offsetX, double offsetY) {}

    public static final class WidgetProfile {
        public String id;
        public String name;

        public WidgetProfile(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class ProfileIndex {
        public String activeProfileId = DEFAULT_PROFILE_ID;
        public List<WidgetProfile> profiles = new ArrayList<>();
    }

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
