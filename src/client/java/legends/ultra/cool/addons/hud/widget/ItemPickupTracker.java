package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudEditorScreen;
import legends.ultra.cool.addons.hud.HudWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ItemPickupTracker extends HudWidget {
    private static final String SHOW_REMOVED_KEY = "showRemoved";
    private static final String DURATION_KEY = "durationSeconds";
    private static final String MAX_ROWS_KEY = "maxRows";
    private static final String GAIN_COLOR_KEY = "gainColor";
    private static final String REMOVE_COLOR_KEY = "removeColor";

    private static final int ITEM_SIZE = 16;
    private static final int ROW_GAP = 2;
    private static final int TEXT_GAP = 5;
    private static final int PAD = 3;
    private static final int TEXT_Y_OFFSET = 1;

    private static ItemPickupTracker instance;

    private final List<InventoryCount> lastCounts = new ArrayList<>();
    private final List<PickupNotification> notifications = new ArrayList<>();

    private boolean hasBaseline = false;

    public ItemPickupTracker(int x, int y) {
        super("Pickup Tracker", x, y);
        instance = this;
    }

    public void tick(PlayerInventory inventory) {
        tickNotifications();

        List<InventoryCount> now = countItems(inventory);
        ItemStack cursorStack = getCursorStack();
        if (!cursorStack.isEmpty()) {
            addToCounts(now, cursorStack);
        }

        if (hasBaseline) {
            diff(lastCounts, now);
        }

        lastCounts.clear();
        lastCounts.addAll(now);
        hasBaseline = true;
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
        for (InventoryCount newCount : newCounts) {
            InventoryCount oldCount = findMatching(oldCounts, newCount.stack);
            int oldAmount = oldCount == null ? 0 : oldCount.count;
            enqueue(newCount.stack, newCount.count - oldAmount);
        }

        for (InventoryCount oldCount : oldCounts) {
            if (findMatching(newCounts, oldCount.stack) == null) {
                enqueue(oldCount.stack, -oldCount.count);
            }
        }
    }

    private void enqueue(ItemStack stack, int delta) {
        if (delta == 0 || stack.isEmpty()) {
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

        int width = notifications.isEmpty() ? getPlaceholderWidth(textRenderer) : getRowsWidth(textRenderer);
        int height = notifications.isEmpty() ? getPlaceholderHeight() : getRowsHeight();
        int left = (int) x;
        int top = getRenderTop(height);

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
        return usesDecoratedBounds() ? x - PAD : x;
    }

    @Override
    public double getVisualY() {
        return usesDecoratedBounds() ? getRenderTop((int) Math.ceil(getHeight())) - PAD : getRenderTop((int) Math.ceil(getHeight()));
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
                HudSetting.toggle(SHOW_REMOVED_KEY, "Show Removed",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, SHOW_REMOVED_KEY, true),
                        b -> WidgetConfigManager.setBool(w, SHOW_REMOVED_KEY, b, true),
                        true
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
                        1f, 8f, 1f,
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

    private int getRenderTop(int height) {
        return (int) y - Math.max(0, height - ITEM_SIZE);
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
        return Math.max(1, Math.min(8, Math.round(maxRows)));
    }

    private static final class InventoryCount {
        private final ItemStack stack;
        private int count;

        private InventoryCount(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
        }
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
}
