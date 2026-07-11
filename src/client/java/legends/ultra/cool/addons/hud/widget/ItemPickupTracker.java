package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudEditorScreen;
import legends.ultra.cool.addons.hud.HudWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ItemPickupTracker extends HudWidget {
    private static final String SHOW_REMOVED_KEY = "showRemoved";
    private static final String DURATION_KEY = "durationSeconds";
    private static final String MAX_ROWS_KEY = "maxRows";
    private static final String GAIN_COLOR_KEY = "gainColor";
    private static final String REMOVE_COLOR_KEY = "removeColor";
    private static final String TRACK_CONTAINERS_KEY = "trackContainers";
    private static final String BLACKLIST_KEY = "blacklist";
    private static final String WHITELIST_KEY = "whitelist";
    private static final String BLACKLIST_TOGGLE_KEY = "blacklistToggle";
    private static final String WHITELIST_TOGGLE_KEY = "whitelistToggle";

    private static final int ITEM_SIZE = 16;
    private static final int ROW_GAP = 2;
    private static final int TEXT_GAP = 5;
    private static final int PAD = 3;
    private static final int TEXT_Y_OFFSET = 1;
    private static final int LOGIN_BASELINE_TICKS = 40;

    private static ItemPickupTracker instance;

    private final List<InventoryCount> lastCounts = new ArrayList<>();
    private final List<PickupNotification> notifications = new ArrayList<>();

    private boolean hasBaseline = false;
    private int baselineOnlyTicks = LOGIN_BASELINE_TICKS;
    private UUID baselinePlayerUuid = null;

    public ItemPickupTracker(int x, int y) {
        super("Pickup Tracker", x, y);
        instance = this;
    }

    public void tick(MinecraftClient client) {
        tickNotifications();
        if (client == null || client.player == null) {
            resetState();
            return;
        }
        if (!client.player.getUuid().equals(baselinePlayerUuid)) {
            resetState();
            baselinePlayerUuid = client.player.getUuid();
        }

        boolean containerOpen = client.currentScreen instanceof HandledScreen<?>;
        boolean trackContainers = WidgetConfigManager.getBool(getName(), TRACK_CONTAINERS_KEY, false);
        boolean baselineOnly = !hasBaseline || baselineOnlyTicks > 0 || (containerOpen && !trackContainers);

        List<InventoryCount> now = countItems(client.player.getInventory());
        ItemStack cursorStack = getCursorStack();
        if (!cursorStack.isEmpty() && (!containerOpen || trackContainers)) {
            addToCounts(now, cursorStack);
        }

        if (!baselineOnly) {
            diff(lastCounts, now);
        }

        lastCounts.clear();
        lastCounts.addAll(now);
        hasBaseline = true;
        if (baselineOnlyTicks > 0) {
            baselineOnlyTicks--;
        }
    }

    private static List<InventoryCount> countItems(PlayerInventory inventory) {
        List<InventoryCount> counts = new ArrayList<>();

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }

            addToCounts(counts, stack);
        }
        return counts;
    }

    private static void addToCounts(List<InventoryCount> counts, ItemStack stack) {
        InventoryCount existing = findMatching(counts, stack);
        if (existing != null) {
            existing.count += stack.getCount();
        } else {
            counts.add(new InventoryCount(stack.copyWithCount(1), stack.getCount()));
        }
    }

    private void diff(List<InventoryCount> oldCounts, List<InventoryCount> newCounts) {
        List<InventoryCount> oldRemaining = copyCounts(oldCounts);
        List<InventoryCount> newRemaining = copyCounts(newCounts);

        cancelMatchingCounts(oldRemaining, newRemaining, MatchMode.EXACT);
        cancelMatchingCounts(oldRemaining, newRemaining, MatchMode.ITEM_ONLY);

        for (InventoryCount newCount : newRemaining) {
            enqueue(newCount.stack, newCount.count);
        }

        for (InventoryCount oldCount : oldRemaining) {
            enqueue(oldCount.stack, -oldCount.count);
        }
    }

    private static List<InventoryCount> copyCounts(List<InventoryCount> counts) {
        List<InventoryCount> copy = new ArrayList<>();
        for (InventoryCount count : counts) {
            copy.add(new InventoryCount(count.stack, count.count));
        }
        return copy;
    }

    private static void cancelMatchingCounts(List<InventoryCount> oldCounts, List<InventoryCount> newCounts, MatchMode mode) {
        for (InventoryCount newCount : newCounts) {
            if (newCount.count <= 0) {
                continue;
            }

            for (InventoryCount oldCount : oldCounts) {
                if (oldCount.count <= 0 || !matches(oldCount.stack, newCount.stack, mode)) {
                    continue;
                }

                int matched = Math.min(oldCount.count, newCount.count);
                oldCount.count -= matched;
                newCount.count -= matched;
                if (newCount.count <= 0) {
                    break;
                }
            }
        }

        oldCounts.removeIf(count -> count.count <= 0);
        newCounts.removeIf(count -> count.count <= 0);
    }

    private static boolean matches(ItemStack oldStack, ItemStack newStack, MatchMode mode) {
        return switch (mode) {
            case EXACT -> ItemStack.areItemsAndComponentsEqual(oldStack, newStack);
            case ITEM_ONLY -> oldStack.isOf(newStack.getItem());
        };
    }

    private void enqueue(ItemStack stack, int delta) {
        if (delta == 0 || stack.isEmpty()) {
            return;
        }
        if (!shouldTrackStack(stack)) {
            return;
        }
        if (delta < 0 && !WidgetConfigManager.getBool(getName(), SHOW_REMOVED_KEY, true)) {
            return;
        }

        switch (getPickupMode()) {
            case DIFF -> enqueueDiff(stack, delta);
            case REPLACE -> enqueueReplace(stack, delta);
            case NONE -> enqueueSeparate(stack, delta);
        }
    }

    private PickupMode getPickupMode() {
        return PickupMode.fromId(WidgetConfigManager.getString(
                getName(),
                PickupMode.SETTING_KEY,
                PickupMode.DEFAULT_ID
        ));
    }

    private void enqueueDiff(ItemStack stack, int delta) {
        int durationTicks = getDurationTicks();
        for (int i = 0; i < notifications.size(); i++) {
            PickupNotification notification = notifications.get(i);
            if (ItemStack.areItemsAndComponentsEqual(notification.stack, stack)) {
                notification.delta += delta;
                if (notification.delta == 0) {
                    notifications.remove(i);
                    return;
                }
                notification.remainingTicks = durationTicks;
                return;
            }
        }

        addNotification(stack, delta, durationTicks);
    }

    private void enqueueReplace(ItemStack stack, int delta) {
        int durationTicks = getDurationTicks();
        for (PickupNotification notification : notifications) {
            if (ItemStack.areItemsAndComponentsEqual(notification.stack, stack)) {
                if (Integer.signum(notification.delta) == Integer.signum(delta)) {
                    notification.delta += delta;
                } else {
                    notification.delta = delta;
                }
                notification.remainingTicks = durationTicks;
                return;
            }
        }

        addNotification(stack, delta, durationTicks);
    }

    private void enqueueSeparate(ItemStack stack, int delta) {
        addNotification(stack, delta, getDurationTicks());
    }

    private void addNotification(ItemStack stack, int delta, int durationTicks) {
        notifications.add(0, new PickupNotification(stack.copyWithCount(1), delta, durationTicks));
        trimNotifications();
    }

    private void trimNotifications() {
        int maxRows = getMaxRows();
        while (notifications.size() > maxRows) {
            notifications.remove(notifications.size() - 1);
        }
    }

    private void tickNotifications() {
        Iterator<PickupNotification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            PickupNotification notification = iterator.next();
            notification.remainingTicks--;
            if (notification.remainingTicks <= 0) {
                iterator.remove();
            }
        }
    }

    public static void reset() {
        if (instance != null) {
            instance.resetState();
        }
    }

    private void resetState() {
        lastCounts.clear();
        notifications.clear();
        hasBaseline = false;
        baselineOnlyTicks = LOGIN_BASELINE_TICKS;
        baselinePlayerUuid = null;
    }

    @Override
    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        TextRenderer textRenderer = client.textRenderer;
        boolean editorPreview = client.currentScreen instanceof HudEditorScreen;
        if (notifications.isEmpty() && !editorPreview) {
            return;
        }

        final String w = getName();
        boolean bgToggle = WidgetConfigManager.getBool(w, "bgToggle", true);
        int bgColor = WidgetConfigManager.getInt(w, "bgColor", 0x80000000);
        boolean brdToggle = WidgetConfigManager.getBool(w, "brdToggle", true);
        int brdColor = WidgetConfigManager.getInt(w, "brdColor", 0xFFFFFFFF);
        int gainColor = WidgetConfigManager.getInt(w, GAIN_COLOR_KEY, 0xFF54FC54);
        int removeColor = WidgetConfigManager.getInt(w, REMOVE_COLOR_KEY, 0xFFFF5555);
        TextAlignment textAlignment = TextAlignment.fromId(
                WidgetConfigManager.getString(w, TextAlignment.SETTING_KEY, TextAlignment.LEFT_DEFAULT_ID)
        );
        WidgetGrowthDirection growthDirection = WidgetGrowthDirection.fromId(
                WidgetConfigManager.getString(w, WidgetGrowthDirection.SETTING_KEY, WidgetGrowthDirection.DEFAULT_ID)
        );

        int width = notifications.isEmpty() ? getPlaceholderWidth(textRenderer) : getRowsWidth(textRenderer);
        int height = notifications.isEmpty() ? getPlaceholderHeight() : getRowsHeight();
        int left = (int) textAlignment.leftX(x, width);
        int top = getRenderTop(height, growthDirection);

        if (bgToggle) {
            context.fill(left - PAD, top - PAD, left + width + PAD, top + height + PAD, bgColor);
        }

        if (brdToggle) {
            drawBorder(context, left - PAD, top - PAD, width + PAD * 2, height + PAD * 2, brdColor);
        }

        if (notifications.isEmpty()) {
            drawPlaceholder(context, textRenderer, left, top, gainColor, removeColor, !bgToggle);
            return;
        }

        for (int i = 0; i < notifications.size(); i++) {
            PickupNotification notification = notifications.get(i);
            int rowY = top + i * (ITEM_SIZE + ROW_GAP);
            int color = notification.delta > 0 ? gainColor : removeColor;

            context.drawItem(notification.stack, left, rowY);
            context.drawText(
                    textRenderer,
                    formatRow(notification),
                    left + ITEM_SIZE + TEXT_GAP,
                    textYForRow(rowY, ITEM_SIZE, textRenderer),
                    color,
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

        return notifications.isEmpty() ? getPlaceholderWidth(client.textRenderer) : getRowsWidth(client.textRenderer);
    }

    @Override
    public double getHeight() {
        return notifications.isEmpty() ? getPlaceholderHeight() : getRowsHeight();
    }

    @Override
    public double getVisualX() {
        double left = currentTextAlignment().leftX(x, getWidth());
        return usesDecoratedBounds() ? left - PAD : left;
    }

    @Override
    public double getVisualY() {
        double top = getRenderTop((int) Math.ceil(getHeight()), currentGrowthDirection());
        return usesDecoratedBounds() ? top - PAD : top;
    }

    @Override
    public double getVisualWidth() {
        return getWidth() + (usesDecoratedBounds() ? PAD * 2d : 0d);
    }

    @Override
    public double getVisualHeight() {
        return getHeight() + (usesDecoratedBounds() ? PAD * 2d : 0d);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public List<HudSetting> getSettings() {
        final String w = getName();
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
                HudSetting.color(GAIN_COLOR_KEY, "Gain Color",
                        () -> true,
                        () -> WidgetConfigManager.getInt(w, GAIN_COLOR_KEY, 0xFF54FC54),
                        c -> WidgetConfigManager.setInt(w, GAIN_COLOR_KEY, c, true),
                        0xFF54FC54
                ),
                HudSetting.color(REMOVE_COLOR_KEY, "Remove Color",
                        () -> WidgetConfigManager.getBool(w, SHOW_REMOVED_KEY, true),
                        () -> WidgetConfigManager.getInt(w, REMOVE_COLOR_KEY, 0xFFFF5555),
                        c -> WidgetConfigManager.setInt(w, REMOVE_COLOR_KEY, c, true),
                        0xFFFF5555
                ),
                HudSetting.section("Alignment"),
                HudSetting.dropdown(TextAlignment.SETTING_KEY, "Text Align",
                        () -> true,
                        () -> WidgetConfigManager.getString(w, TextAlignment.SETTING_KEY, TextAlignment.LEFT_DEFAULT_ID),
                        value -> TextAlignment.setForWidgetPreservingLeft(this, value, TextAlignment.LEFT_DEFAULT_ID),
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
                HudSetting.section("Filters"),
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
                                List.of(CustomField.text("item", "Item", "Name or id", true, 80)),
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
                                List.of(CustomField.text("item", "Item", "Name or id", true, 80)),
                                () -> getItemFilterEntries(WHITELIST_KEY),
                                entries -> setItemFilterEntries(WHITELIST_KEY, entries),
                                entry -> entry.get("item"),
                                entry -> ""
                        )
                ),
                HudSetting.section("Other"),
                HudSetting.toggle(SHOW_REMOVED_KEY, "Show Removed",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, SHOW_REMOVED_KEY, true),
                        b -> WidgetConfigManager.setBool(w, SHOW_REMOVED_KEY, b, true),
                        true
                ),
                HudSetting.toggle(TRACK_CONTAINERS_KEY, "Track Containers",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, TRACK_CONTAINERS_KEY, false),
                        b -> WidgetConfigManager.setBool(w, TRACK_CONTAINERS_KEY, b, true),
                        false
                ),
                HudSetting.dropdown(PickupMode.SETTING_KEY, "Pickup Mode",
                        () -> true,
                        () -> WidgetConfigManager.getString(w, PickupMode.SETTING_KEY, PickupMode.DEFAULT_ID),
                        value -> {
                            WidgetConfigManager.setString(w, PickupMode.SETTING_KEY, value, true);
                            notifications.clear();
                        },
                        PickupMode.DEFAULT_ID,
                        PickupMode.options()
                ),
                HudSetting.slider(DURATION_KEY, "Duration",
                        1f, 10f, 0.5f,
                        () -> true,
                        () -> WidgetConfigManager.getFloat(w, DURATION_KEY, 3f),
                        value -> WidgetConfigManager.setFloat(w, DURATION_KEY, (float) value, true),
                        3f
                ),
                HudSetting.slider(MAX_ROWS_KEY, "Max Rows",
                        1f, 20f, 1f,
                        () -> true,
                        () -> WidgetConfigManager.getFloat(w, MAX_ROWS_KEY, 4f),
                        value -> WidgetConfigManager.setFloat(w, MAX_ROWS_KEY, (float) value, true),
                        4f
                )
        );
    }

    private void drawPlaceholder(DrawContext context, TextRenderer textRenderer, int x, int y, int colorAdd, int colorRemove, boolean shadow) {
        int secondRowY = y + ITEM_SIZE + ROW_GAP;

        context.drawItem(new ItemStack(Items.DIRT), x, y);
        context.drawText(textRenderer, "+1 Item", x + ITEM_SIZE + TEXT_GAP, textYForRow(y, ITEM_SIZE, textRenderer), colorAdd, shadow);
        context.drawItem(new ItemStack(Items.DIRT), x, secondRowY);
        context.drawText(textRenderer, "-1 Item", x + ITEM_SIZE + TEXT_GAP, textYForRow(secondRowY, ITEM_SIZE, textRenderer), colorRemove, shadow);
    }

    private boolean usesDecoratedBounds() {
        return WidgetConfigManager.getBool(getName(), "bgToggle", true)
                || WidgetConfigManager.getBool(getName(), "brdToggle", true);
    }

    private int getRenderTop(int height, WidgetGrowthDirection growthDirection) {
        return growthDirection.topY(y, height, ITEM_SIZE);
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

    private static InventoryCount findMatching(List<InventoryCount> counts, ItemStack stack) {
        for (InventoryCount count : counts) {
            if (ItemStack.areItemsAndComponentsEqual(count.stack, stack)) {
                return count;
            }
        }
        return null;
    }

    private static ItemStack getCursorStack() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.currentScreenHandler == null) {
            return ItemStack.EMPTY;
        }

        return client.player.currentScreenHandler.getCursorStack();
    }

    private int getRowsWidth(TextRenderer textRenderer) {
        int maxTextWidth = 0;
        for (PickupNotification notification : notifications) {
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth(formatRow(notification)));
        }
        return ITEM_SIZE + TEXT_GAP + maxTextWidth;
    }

    private int getRowsHeight() {
        return notifications.size() * ITEM_SIZE + Math.max(0, notifications.size() - 1) * ROW_GAP;
    }

    private static int getPlaceholderWidth(TextRenderer textRenderer) {
        return ITEM_SIZE + TEXT_GAP + textRenderer.getWidth("+1 Item");
    }

    private static int getPlaceholderHeight() {
        return ITEM_SIZE * 2 + ROW_GAP;
    }

    private static int textYForRow(int rowY, int rowHeight, TextRenderer textRenderer) {
        return rowY + (rowHeight - textRenderer.fontHeight) / 2 + TEXT_Y_OFFSET;
    }

    private String formatRow(PickupNotification notification) {
        String sign = notification.delta > 0 ? "+" : "-";
        return sign + Math.abs(notification.delta) + " " + notification.stack.getName().getString();
    }

    private int getDurationTicks() {
        float seconds = WidgetConfigManager.getFloat(getName(), DURATION_KEY, 3f);
        return Math.max(1, Math.round(seconds * 20f));
    }

    private int getMaxRows() {
        float maxRows = WidgetConfigManager.getFloat(getName(), MAX_ROWS_KEY, 4f);
        return Math.max(1, Math.min(20, Math.round(maxRows)));
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

    private boolean shouldTrackStack(ItemStack stack) {
        boolean whitelistEnabled = WidgetConfigManager.getBool(getName(), WHITELIST_TOGGLE_KEY, false);
        boolean blacklistEnabled = WidgetConfigManager.getBool(getName(), BLACKLIST_TOGGLE_KEY, false);

        if (whitelistEnabled) {
            return matchesAnyFilter(stack, WHITELIST_KEY);
        }
        if (blacklistEnabled) {
            return !matchesAnyFilter(stack, BLACKLIST_KEY);
        }
        return true;
    }

    private boolean matchesAnyFilter(ItemStack stack, String key) {
        Set<String> names = stackFilterNames(stack);
        for (ItemFilter filter : WidgetConfigManager.getObjectList(getName(), key, ItemFilter.class)) {
            if (filter == null || filter.item == null || filter.item.isBlank()) {
                continue;
            }

            String normalized = normalizeFilterName(filter.item);
            if (names.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private List<CustomEntry> getItemFilterEntries(String key) {
        List<CustomEntry> entries = new ArrayList<>();
        for (ItemFilter filter : WidgetConfigManager.getObjectList(getName(), key, ItemFilter.class)) {
            if (filter == null || filter.item == null || filter.item.isBlank()) {
                continue;
            }

            Map<String, String> values = new LinkedHashMap<>();
            values.put("item", filter.item);
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

    private static Set<String> stackFilterNames(ItemStack stack) {
        Set<String> names = new HashSet<>();
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        names.add(normalizeFilterName(id));
        int colon = id.indexOf(':');
        if (colon >= 0 && colon + 1 < id.length()) {
            names.add(normalizeFilterName(id.substring(colon + 1)));
        }
        names.add(normalizeFilterName(stack.getName().getString()));
        return names;
    }

    private static String normalizeFilterName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static final class InventoryCount {
        private final ItemStack stack;
        private int count;

        private InventoryCount(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
        }
    }

    private enum MatchMode {
        EXACT,
        ITEM_ONLY
    }

    private static final class PickupNotification {
        private final ItemStack stack;
        private int delta;
        private int remainingTicks;

        private PickupNotification(ItemStack stack, int delta, int remainingTicks) {
            this.stack = stack;
            this.delta = delta;
            this.remainingTicks = remainingTicks;
        }
    }

    private static final class ItemFilter {
        public String item;

        public ItemFilter() {
        }

        private ItemFilter(String item) {
            this.item = item;
        }
    }
}
