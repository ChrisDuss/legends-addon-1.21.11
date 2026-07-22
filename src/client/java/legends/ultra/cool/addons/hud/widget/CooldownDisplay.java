package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudEditorScreen;
import legends.ultra.cool.addons.hud.HudWidget;
import legends.ultra.cool.addons.mixin.client.ItemCooldownEntryAccessor;
import legends.ultra.cool.addons.mixin.client.ItemCooldownManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CooldownDisplay extends HudWidget {
    private static final String CUSTOM_COOLDOWNS_KEY = "customCooldowns";
    private static final String BLACKLIST_KEY = "blacklist";
    private static final String WHITELIST_KEY = "whitelist";
    private static final String BLACKLIST_TOGGLE_KEY = "blacklistToggle";
    private static final String WHITELIST_TOGGLE_KEY = "whitelistToggle";
    private static final int ITEM_SIZE = 16;
    private static final int ROW_GAP = 2;
    private static final int TEXT_GAP = 5;
    private static final int PAD = 3;

    private static CooldownDisplay instance;

    private final Map<String, String> knownCooldowns = new HashMap<>();
    private final Map<String, ActiveCustomCooldown> activeCustomCooldowns = new HashMap<>();

    public CooldownDisplay(int x, int y) {
        super("Cooldown Display", x, y);
        instance = this;
    }

    public static boolean captureChatMessage(Text message, boolean serverMessage) {
        if (instance == null || message == null || !instance.isEnabled()) {
            return false;
        }
        return instance.handleChatMessage(message.getString(), serverMessage);
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            knownCooldowns.clear();
            activeCustomCooldowns.clear();
            return;
        }

        List<CooldownRow> rows = getCooldownRows(client);
        Map<String, String> activeCooldowns = new HashMap<>();
        for (CooldownRow row : rows) {
            activeCooldowns.put(row.key(), row.readyName());
        }

        boolean titleToggle = WidgetConfigManager.getBool(getName(), "titleToggle", true);
        if (titleToggle && !knownCooldowns.isEmpty()) {
            List<String> finishedNames = new ArrayList<>();
            for (Map.Entry<String, String> entry : knownCooldowns.entrySet()) {
                if (!activeCooldowns.containsKey(entry.getKey())
                        && shouldShowReadyTitle(entry.getValue())) {
                    finishedNames.add(entry.getValue());
                }
            }

            if (!finishedNames.isEmpty()) {
                showReadyTitle(client, finishedNames);
            }
        }

        knownCooldowns.clear();
        knownCooldowns.putAll(activeCooldowns);
    }

    @Override
    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean editorPreview = client.currentScreen instanceof HudEditorScreen;
        if (client.player == null && !editorPreview) return;

        TextRenderer textRenderer = client.textRenderer;
        List<CooldownRow> rows = getCooldownRows(client);
        if (rows.isEmpty() && !editorPreview) return;

        final String w = getName();
        boolean bgToggle = WidgetConfigManager.getBool(w, "bgToggle", true);
        int bgColor = WidgetConfigManager.getInt(w, "bgColor", 0x80000000);
        boolean brdToggle = WidgetConfigManager.getBool(w, "brdToggle", true);
        int brdColor = WidgetConfigManager.getInt(w, "brdColor", 0xFFFFFFFF);
        int textColor = WidgetConfigManager.getInt(w, "textColor", 0xFFFFFFFF);
        TextAlignment textAlignment = TextAlignment.fromId(
                WidgetConfigManager.getString(w, TextAlignment.SETTING_KEY, TextAlignment.LEFT_DEFAULT_ID)
        );
        WidgetGrowthDirection growthDirection = WidgetGrowthDirection.fromId(
                WidgetConfigManager.getString(w, WidgetGrowthDirection.SETTING_KEY, WidgetGrowthDirection.DEFAULT_ID)
        );

        int width = rows.isEmpty() ? getPlaceholderWidth(textRenderer) : getRowsWidth(textRenderer, rows);
        width += 1;
        int height = rows.isEmpty() ? ITEM_SIZE : getRowsHeight(rows);
        height -= 1;
        int left = (int) textAlignment.leftX(x, width);
        int top = getRenderTop(height, growthDirection);

        if (bgToggle) {
            context.fill(left - PAD, top - PAD, left + width + PAD, top + height + PAD, bgColor);
        }

        if (brdToggle) {
            drawBorder(context, left - PAD, top - PAD, width + PAD * 2, height + PAD * 2, brdColor);
        }

        if (rows.isEmpty()) {
            drawPlaceholder(context, textRenderer, left + 1, top, textColor, !bgToggle);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            CooldownRow row = rows.get(i);
            int rowY = top + i * (ITEM_SIZE + ROW_GAP);
            String time = formatSeconds(row.remainingTicks() / 20.0f);
            String rowText = row.displayLabel().isBlank() ? time : row.displayLabel() + " " + time;

            context.drawItem(row.stack(), left, rowY);
            context.drawText(
                    textRenderer,
                    rowText,
                    left + ITEM_SIZE + TEXT_GAP,
                    rowY + (ITEM_SIZE - textRenderer.fontHeight) / 2,
                    textColor,
                    !bgToggle
            );
        }
    }

    @Override
    public double getWidth() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return getPlaceholderWidth(client.textRenderer);
        }

        List<CooldownRow> rows = getCooldownRows(client);
        int width = rows.isEmpty() ? getPlaceholderWidth(client.textRenderer) : getRowsWidth(client.textRenderer, rows);
        return width;
    }

    @Override
    public double getHeight() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return ITEM_SIZE;

        List<CooldownRow> rows = getCooldownRows(client);
        return rows.isEmpty() ? ITEM_SIZE : getRowsHeight(rows);
    }

    @Override
    public double getVisualX() {
        double left = currentTextAlignment().leftX(x, getRenderWidth());
        return usesDecoratedBounds() ? left - PAD : left;
    }

    @Override
    public double getVisualY() {
        double top = getRenderTop(getRenderHeight(), currentGrowthDirection());
        return usesDecoratedBounds() ? top - PAD : top;
    }

    @Override
    public double getVisualWidth() {
        return getRenderWidth() + (usesDecoratedBounds() ? PAD * 2d : 0d);
    }

    @Override
    public double getVisualHeight() {
        return getRenderHeight() + (usesDecoratedBounds() ? PAD * 2d : 0d);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public List<HudSetting> getSettings() {
        final String w = this.getName();
        normalizeFilterMode();

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
                        value -> TextAlignment.setForWidgetPreservingLeft(this, value, TextAlignment.LEFT_DEFAULT_ID, getRenderWidth()),
                        TextAlignment.LEFT_DEFAULT_ID,
                        TextAlignment.options()
                ),
                HudSetting.dropdown(WidgetGrowthDirection.SETTING_KEY, "Grow Direction",
                        () -> true,
                        () -> WidgetConfigManager.getString(w, WidgetGrowthDirection.SETTING_KEY, WidgetGrowthDirection.DEFAULT_ID),
                        value -> WidgetConfigManager.setString(w, WidgetGrowthDirection.SETTING_KEY, value, true),
                        WidgetGrowthDirection.DEFAULT_ID,
                        WidgetGrowthDirection.options()
                ),
                HudSetting.section("Alerts filter"),
                HudSetting.toggle(BLACKLIST_TOGGLE_KEY, "Enable blacklist",
                        () -> !WidgetConfigManager.getBool(w, WHITELIST_TOGGLE_KEY, false),
                        () -> WidgetConfigManager.getBool(w, BLACKLIST_TOGGLE_KEY, false),
                        enabled -> setFilterMode(BLACKLIST_TOGGLE_KEY, WHITELIST_TOGGLE_KEY, enabled),
                        false
                ),
                HudSetting.customList(
                        BLACKLIST_KEY,
                        "Blacklist",
                        () -> WidgetConfigManager.getBool(w, BLACKLIST_TOGGLE_KEY, false),
                        new CustomListSpec(
                                List.of(CustomField.text("item", "Item name", "Item name", true, 80)),
                                () -> getItemFilterEntries(BLACKLIST_KEY),
                                entries -> setItemFilterEntries(BLACKLIST_KEY, entries),
                                entry -> entry.get("item"),
                                entry -> ""
                        )
                ),
                HudSetting.toggle(WHITELIST_TOGGLE_KEY, "Enable whitelist",
                        () -> !WidgetConfigManager.getBool(w, BLACKLIST_TOGGLE_KEY, false),
                        () -> WidgetConfigManager.getBool(w, WHITELIST_TOGGLE_KEY, false),
                        enabled -> setFilterMode(WHITELIST_TOGGLE_KEY, BLACKLIST_TOGGLE_KEY, enabled),
                        false
                ),
                HudSetting.customList(
                        WHITELIST_KEY,
                        "Whitelist",
                        () -> WidgetConfigManager.getBool(w, WHITELIST_TOGGLE_KEY, false),
                        new CustomListSpec(
                                List.of(CustomField.text("item", "Item name", "Item name", true, 80)),
                                () -> getItemFilterEntries(WHITELIST_KEY),
                                entries -> setItemFilterEntries(WHITELIST_KEY, entries),
                                entry -> entry.get("item"),
                                entry -> ""
                        )
                ),
                HudSetting.section("Other"),
                HudSetting.customList(
                        CUSTOM_COOLDOWNS_KEY,
                        "Custom Cooldowns",
                        () -> true,
                        new CustomListSpec(
                                List.of(
                                        CustomField.text("title", "Title", "Display name", true, 40),
                                        CustomField.text("prefix", "Prefix", "Prefix (optional)", false, 80),
                                        CustomField.text("message", "Message", "Message", true, 240),
                                        CustomField.positiveNumber("time", "Time (sec)", "Cooldown (seconds)", true)
                                ),
                                this::getCustomCooldownEntries,
                                this::setCustomCooldownEntries,
                                entry -> entry.get("title"),
                                entry -> {
                                    String source = entry.get("prefix").isBlank() ? "Server" : entry.get("prefix");
                                    return source + " | " + formatConfiguredSeconds(entry.get("time"));
                                }
                        )
                ),
                HudSetting.toggle("titleToggle", "Ready Title",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, "titleToggle", false),
                        b -> WidgetConfigManager.setBool(w, "titleToggle", b, true),
                        false
                )
        );
    }

    private List<CooldownRow> getCooldownRows(MinecraftClient client) {
        if (client.player == null) return List.of();

        ItemCooldownManager cooldowns = client.player.getItemCooldownManager();
        ItemCooldownManagerAccessor cooldownAccessor = (ItemCooldownManagerAccessor) cooldowns;
        Map<Identifier, ?> entries = cooldownAccessor.legends$getEntries();

        int now = cooldownAccessor.legends$getTick();
        Map<Identifier, CooldownTiming> active = new HashMap<>();
        for (Map.Entry<Identifier, ?> entry : entries.entrySet()) {
            ItemCooldownEntryAccessor cooldown = (ItemCooldownEntryAccessor) entry.getValue();
            int startTick = cooldown.legends$getStartTick();
            int totalTicks = cooldown.legends$getEndTick() - startTick;
            int remainingTicks = cooldown.legends$getEndTick() - now;
            if (totalTicks > 0 && remainingTicks > 0) {
                active.put(entry.getKey(), new CooldownTiming(startTick, totalTicks, remainingTicks));
            }
        }

        List<CooldownRow> rows = new ArrayList<>();
        Set<Identifier> seen = new HashSet<>();

        for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;

            Identifier group = cooldowns.getGroup(stack);
            CooldownTiming timing = active.get(group);
            if (timing != null && seen.add(group)) {
                rows.add(new CooldownRow(
                        "item:" + group,
                        stack.copyWithCount(1),
                        "",
                        stack.getName().getString(),
                        timing.startTick(),
                        timing.totalTicks(),
                        timing.remainingTicks()
                ));
            }
        }

        for (Map.Entry<Identifier, CooldownTiming> entry : active.entrySet()) {
            if (!seen.add(entry.getKey())) continue;

            ItemStack stack = stackFromCooldownGroup(entry.getKey());
            if (!stack.isEmpty()) {
                CooldownTiming timing = entry.getValue();
                rows.add(new CooldownRow(
                        "item:" + entry.getKey(),
                        stack,
                        "",
                        stack.getName().getString(),
                        timing.startTick(),
                        timing.totalTicks(),
                        timing.remainingTicks()
                ));
            }
        }

        long customNow = client.inGameHud.getTicks();
        Iterator<Map.Entry<String, ActiveCustomCooldown>> iterator = activeCustomCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ActiveCustomCooldown> activeEntry = iterator.next();
            ActiveCustomCooldown custom = activeEntry.getValue();
            long remainingTicks = custom.endTick() - customNow;
            if (remainingTicks <= 0) {
                iterator.remove();
                continue;
            }

            rows.add(new CooldownRow(
                    "custom:" + activeEntry.getKey(),
                    new ItemStack(Items.CLOCK),
                    custom.cooldown().title(),
                    custom.cooldown().title(),
                    custom.startTick(),
                    custom.totalTicks(),
                    remainingTicks
            ));
        }

        rows.sort((a, b) -> Long.compare(b.startTick(), a.startTick()));
        return rows;
    }

    private static ItemStack stackFromCooldownGroup(Identifier group) {
        Item item = Registries.ITEM.get(group);
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    private static int getRowsWidth(TextRenderer textRenderer, List<CooldownRow> rows) {
        int maxTextWidth = 0;
        for (CooldownRow row : rows) {
            String time = formatSeconds(row.remainingTicks() / 20.0f);
            String rowText = row.displayLabel().isBlank() ? time : row.displayLabel() + " " + time;
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth(rowText));
        }
        return ITEM_SIZE + TEXT_GAP + maxTextWidth;
    }

    private static int getRowsHeight(List<CooldownRow> rows) {
        return rows.size() * ITEM_SIZE + Math.max(0, rows.size() - 1) * ROW_GAP;
    }

    private static int getPlaceholderWidth(TextRenderer textRenderer) {
        return ITEM_SIZE + TEXT_GAP + textRenderer.getWidth("0.0s");
    }

    private boolean usesDecoratedBounds() {
        return WidgetConfigManager.getBool(getName(), "bgToggle", true)
                || WidgetConfigManager.getBool(getName(), "brdToggle", true);
    }

    private int getRenderTop(int height, WidgetGrowthDirection growthDirection) {
        return growthDirection.topY(y, height, ITEM_SIZE);
    }

    private int getRenderWidth() {
        return (int) Math.ceil(getWidth()) + 1;
    }

    private int getRenderHeight() {
        return Math.max(1, (int) Math.ceil(getHeight()) - 1);
    }

    private TextAlignment currentTextAlignment() {
        return TextAlignment.fromId(
                WidgetConfigManager.getString(getName(), TextAlignment.SETTING_KEY, TextAlignment.LEFT_DEFAULT_ID)
        );
    }

    private WidgetGrowthDirection currentGrowthDirection() {
        return WidgetGrowthDirection.fromId(
                WidgetConfigManager.getString(getName(), WidgetGrowthDirection.SETTING_KEY, WidgetGrowthDirection.DEFAULT_ID)
        );
    }

    private static void showReadyTitle(MinecraftClient client, List<String> finishedNames) {
        String title = finishedNames.size() == 1
                ? finishedNames.get(0) + " Ready"
                : finishedNames.size() + " Cooldowns Ready";

        client.inGameHud.setTitleTicks(5, 25, 5);
        client.inGameHud.setSubtitle(Text.empty());
        client.inGameHud.setTitle(Text.literal(title));
    }

    private static void drawPlaceholder(DrawContext context, TextRenderer textRenderer, int x, int y, int color, boolean shadow) {
        context.drawItem(new ItemStack(Items.CLOCK), x, y);
        context.drawText(textRenderer, "0.0s", x + ITEM_SIZE + TEXT_GAP, y + (ITEM_SIZE - textRenderer.fontHeight) / 2 + 1, color, shadow);
    }

    private static String formatSeconds(float seconds) {
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }

    private boolean handleChatMessage(String rawMessage, boolean serverMessage) {
        String normalizedMessage = normalizeChatText(rawMessage);
        if (normalizedMessage.isBlank()) {
            return false;
        }

        long now = MinecraftClient.getInstance().inGameHud.getTicks();
        boolean triggered = false;
        for (CustomCooldown cooldown : getCustomCooldowns()) {
            if (!matches(cooldown, normalizedMessage, serverMessage)) {
                continue;
            }

            triggered = true;
            long totalTicks = Math.max(1L, Math.round(cooldown.timeSeconds() * 20d));
            totalTicks = Math.min(totalTicks, Integer.MAX_VALUE / 4L);
            activeCustomCooldowns.put(
                    cooldown.id(),
                    new ActiveCustomCooldown(cooldown, now, now + totalTicks, totalTicks)
            );
        }
        return triggered;
    }

    private List<CustomEntry> getCustomCooldownEntries() {
        List<CustomEntry> entries = new ArrayList<>();
        for (CustomCooldown cooldown : getCustomCooldowns()) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("id", cooldown.id());
            values.put("title", cooldown.title());
            values.put("prefix", cooldown.prefix());
            values.put("message", cooldown.message());
            values.put("time", formatRawSeconds(cooldown.timeSeconds()));
            entries.add(new CustomEntry(values));
        }
        return List.copyOf(entries);
    }

    private void setFilterMode(String enabledKey, String disabledKey, boolean enabled) {
        WidgetConfigManager.setBool(getName(), enabledKey, enabled, false);
        if (enabled) {
            WidgetConfigManager.setBool(getName(), disabledKey, false, false);
        }
        WidgetConfigManager.save();
    }

    private void normalizeFilterMode() {
        boolean blacklistEnabled = WidgetConfigManager.getBool(getName(), BLACKLIST_TOGGLE_KEY, false);
        boolean whitelistEnabled = WidgetConfigManager.getBool(getName(), WHITELIST_TOGGLE_KEY, false);
        if (blacklistEnabled && whitelistEnabled) {
            WidgetConfigManager.setBool(getName(), WHITELIST_TOGGLE_KEY, false, true);
        }
    }

    private List<CustomEntry> getItemFilterEntries(String key) {
        List<CustomEntry> entries = new ArrayList<>();
        for (ItemFilter filter : WidgetConfigManager.getObjectList(getName(), key, ItemFilter.class)) {
            if (filter == null || filter.item() == null || filter.item().isBlank()) {
                continue;
            }

            Map<String, String> values = new LinkedHashMap<>();
            values.put("item", filter.item());
            entries.add(new CustomEntry(values));
        }
        return List.copyOf(entries);
    }

    private void setItemFilterEntries(String key, List<CustomEntry> entries) {
        List<ItemFilter> filters = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CustomEntry entry : entries) {
            String item = entry.get("item").trim();
            String normalized = normalizeFilterName(item);
            if (!item.isBlank() && seen.add(normalized)) {
                filters.add(new ItemFilter(item));
            }
        }
        WidgetConfigManager.setObjectList(getName(), key, filters, true);
    }

    private boolean shouldShowReadyTitle(String readyName) {
        String titleName = normalizeFilterName(readyName);
        boolean whitelistEnabled = WidgetConfigManager.getBool(getName(), WHITELIST_TOGGLE_KEY, false);
        boolean blacklistEnabled = WidgetConfigManager.getBool(getName(), BLACKLIST_TOGGLE_KEY, false);

        if (whitelistEnabled) {
            return getNormalizedItemFilter(WHITELIST_KEY).contains(titleName);
        }
        if (blacklistEnabled) {
            return !getNormalizedItemFilter(BLACKLIST_KEY).contains(titleName);
        }
        return true;
    }

    private Set<String> getNormalizedItemFilter(String key) {
        Set<String> names = new HashSet<>();
        for (ItemFilter filter : WidgetConfigManager.getObjectList(getName(), key, ItemFilter.class)) {
            if (filter != null && filter.item() != null && !filter.item().isBlank()) {
                names.add(normalizeFilterName(filter.item()));
            }
        }
        return names;
    }

    private static String normalizeFilterName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private void setCustomCooldownEntries(List<CustomEntry> entries) {
        List<CustomCooldown> cooldowns = new ArrayList<>();
        for (CustomEntry entry : entries) {
            String title = entry.get("title").trim();
            String message = entry.get("message").trim();
            double seconds;
            try {
                seconds = Double.parseDouble(entry.get("time"));
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (title.isBlank() || message.isBlank() || !Double.isFinite(seconds) || seconds <= 0d) {
                continue;
            }

            String id = entry.get("id").trim();
            if (id.isBlank()) {
                id = UUID.randomUUID().toString();
            }

            cooldowns.add(new CustomCooldown(id, title, entry.get("prefix").trim(), message, seconds));
        }

        WidgetConfigManager.setObjectList(getName(), CUSTOM_COOLDOWNS_KEY, cooldowns, true);
        activeCustomCooldowns.clear();
    }

    private List<CustomCooldown> getCustomCooldowns() {
        List<CustomCooldown> loaded = WidgetConfigManager.getObjectList(
                getName(),
                CUSTOM_COOLDOWNS_KEY,
                CustomCooldown.class
        );
        List<CustomCooldown> valid = new ArrayList<>();
        boolean changed = false;

        for (CustomCooldown cooldown : loaded) {
            if (cooldown == null || cooldown.message() == null
                    || cooldown.message().isBlank()
                    || !Double.isFinite(cooldown.timeSeconds())
                    || cooldown.timeSeconds() <= 0d) {
                changed = true;
                continue;
            }

            String id = cooldown.id();
            if (id == null || id.isBlank()) {
                id = UUID.randomUUID().toString();
                changed = true;
            }

            String title = cooldown.title();
            if (title == null || title.isBlank()) {
                title = compactLabel(cooldown.message());
                changed = true;
            } else {
                String trimmedTitle = title.trim();
                if (!trimmedTitle.equals(title)) {
                    changed = true;
                }
                title = trimmedTitle;
            }

            String prefix = cooldown.prefix() == null ? "" : cooldown.prefix().trim();
            String message = cooldown.message().trim();
            if (!prefix.equals(cooldown.prefix()) || !message.equals(cooldown.message())) {
                changed = true;
            }
            valid.add(new CustomCooldown(id, title, prefix, message, cooldown.timeSeconds()));
        }

        if (changed) {
            WidgetConfigManager.setObjectList(getName(), CUSTOM_COOLDOWNS_KEY, valid, true);
        }
        return List.copyOf(valid);
    }

    private static boolean matches(CustomCooldown cooldown, String normalizedChat, boolean serverMessage) {
        String message = normalizeChatText(cooldown.message());
        if (message.isBlank()) {
            return false;
        }

        String prefix = normalizeChatText(cooldown.prefix());
        if (prefix.isBlank()) {
            return serverMessage && normalizedChat.equals(message);
        }

        if (!normalizedChat.startsWith(prefix)) {
            return false;
        }

        if (normalizedChat.length() > prefix.length()
                && Character.isLetterOrDigit(prefix.charAt(prefix.length() - 1))
                && Character.isLetterOrDigit(normalizedChat.charAt(prefix.length()))) {
            return false;
        }

        String remainingMessage = stripLeadingMessageSeparators(normalizedChat.substring(prefix.length()));
        return remainingMessage.equals(message);
    }

    private static String stripLeadingMessageSeparators(String value) {
        int start = 0;
        while (start < value.length()) {
            char character = value.charAt(start);
            if (!Character.isWhitespace(character)
                    && character != ':'
                    && character != '>'
                    && character != '-'
                    && character != '|') {
                break;
            }
            start++;
        }
        return value.substring(start).trim();
    }

    private static String normalizeChatText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private static String compactLabel(String message) {
        String trimmed = message == null ? "" : message.trim();
        return trimmed.length() <= 24 ? trimmed : trimmed.substring(0, 21) + "...";
    }

    private static String formatRawSeconds(double seconds) {
        if (seconds == Math.rint(seconds)) {
            return Long.toString((long) seconds);
        }
        return Double.toString(seconds);
    }

    private static String formatConfiguredSeconds(String rawSeconds) {
        try {
            return formatRawSeconds(Double.parseDouble(rawSeconds)) + "s";
        } catch (NumberFormatException ignored) {
            return rawSeconds + "s";
        }
    }

    private record CooldownTiming(int startTick, int totalTicks, int remainingTicks) {
    }

    private record CooldownRow(
            String key,
            ItemStack stack,
            String displayLabel,
            String readyName,
            long startTick,
            long totalTicks,
            long remainingTicks
    ) {
    }

    private record CustomCooldown(String id, String title, String prefix, String message, double timeSeconds) {
    }

    private record ItemFilter(String item) {
    }

    private record ActiveCustomCooldown(
            CustomCooldown cooldown,
            long startTick,
            long endTick,
            long totalTicks
    ) {
    }
}
