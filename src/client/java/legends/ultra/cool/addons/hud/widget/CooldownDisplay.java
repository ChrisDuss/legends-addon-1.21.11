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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CooldownDisplay extends HudWidget {
    private static final int ITEM_SIZE = 16;
    private static final int ROW_GAP = 2;
    private static final int TEXT_GAP = 5;
    private static final int PAD = 3;

    private final Map<Identifier, String> knownCooldowns = new HashMap<>();

    public CooldownDisplay(int x, int y) {
        super("Cooldown Display", x, y);
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            knownCooldowns.clear();
            return;
        }

        List<CooldownRow> rows = getCooldownRows(client);
        Map<Identifier, String> activeCooldowns = new HashMap<>();
        for (CooldownRow row : rows) {
            activeCooldowns.put(row.group(), row.stack().getName().getString());
        }

        boolean titleToggle = WidgetConfigManager.getBool(getName(), "titleToggle", true);
        if (titleToggle && !knownCooldowns.isEmpty()) {
            List<String> finishedNames = new ArrayList<>();
            for (Map.Entry<Identifier, String> entry : knownCooldowns.entrySet()) {
                if (!activeCooldowns.containsKey(entry.getKey())) {
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
        if (client.player == null) return;

        TextRenderer textRenderer = client.textRenderer;
        List<CooldownRow> rows = getCooldownRows(client);
        boolean editorPreview = client.currentScreen instanceof HudEditorScreen;
        if (rows.isEmpty() && !editorPreview) return;

        final String w = getName();
        boolean bgToggle = WidgetConfigManager.getBool(w, "bgToggle", true);
        int bgColor = WidgetConfigManager.getInt(w, "bgColor", 0x80000000);
        boolean brdToggle = WidgetConfigManager.getBool(w, "brdToggle", true);
        int brdColor = WidgetConfigManager.getInt(w, "brdColor", 0xFFFFFFFF);
        int textColor = WidgetConfigManager.getInt(w, "textColor", 0xFFFFFFFF);

        int width = rows.isEmpty() ? getPlaceholderWidth(textRenderer) : getRowsWidth(textRenderer, rows);
        int height = rows.isEmpty() ? ITEM_SIZE : getRowsHeight(rows);
        int left = (int) x;
        int top = getRenderTop(height);

        if (bgToggle) {
            context.fill(left - PAD, top - PAD, left + width + PAD, top + height + PAD, bgColor);
        }

        if (brdToggle) {
            drawBorder(context, left - PAD, top - PAD, width + PAD * 2, height + PAD * 2, brdColor);
        }

        if (rows.isEmpty()) {
            drawPlaceholder(context, textRenderer, left, top, textColor, !bgToggle);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            CooldownRow row = rows.get(i);
            int rowY = top + i * (ITEM_SIZE + ROW_GAP);
            String time = formatSeconds(row.remainingTicks() / 20.0f);

            context.drawItem(row.stack(), left, rowY);
            context.drawText(
                    textRenderer,
                    time,
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
        return rows.isEmpty() ? getPlaceholderWidth(client.textRenderer) : getRowsWidth(client.textRenderer, rows);
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
        final String w = this.getName();

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

                HudSetting.color("textColor", "Text Color",
                        () -> true,
                        () -> WidgetConfigManager.getInt(w, "textColor", 0xFFFFFFFF),
                        c -> WidgetConfigManager.setInt(w, "textColor", c, true),
                        0xFFFFFFFF
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
        if (entries.isEmpty()) return List.of();

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

        if (active.isEmpty()) return List.of();

        List<CooldownRow> rows = new ArrayList<>();
        Set<Identifier> seen = new HashSet<>();

        for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;

            Identifier group = cooldowns.getGroup(stack);
            CooldownTiming timing = active.get(group);
            if (timing != null && seen.add(group)) {
                rows.add(new CooldownRow(group, stack.copyWithCount(1), timing.startTick(), timing.totalTicks(), timing.remainingTicks()));
            }
        }

        for (Map.Entry<Identifier, CooldownTiming> entry : active.entrySet()) {
            if (!seen.add(entry.getKey())) continue;

            ItemStack stack = stackFromCooldownGroup(entry.getKey());
            if (!stack.isEmpty()) {
                CooldownTiming timing = entry.getValue();
                rows.add(new CooldownRow(entry.getKey(), stack, timing.startTick(), timing.totalTicks(), timing.remainingTicks()));
            }
        }

        rows.sort((a, b) -> Integer.compare(b.startTick(), a.startTick()));
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
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth(time));
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

    private int getRenderTop(int height) {
        return (int) y - Math.max(0, height - ITEM_SIZE);
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
        context.drawText(textRenderer, "0.0s", x + ITEM_SIZE + TEXT_GAP, y + (ITEM_SIZE - textRenderer.fontHeight) / 2, color, shadow);
    }

    private static String formatSeconds(float seconds) {
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }

    private record CooldownTiming(int startTick, int totalTicks, int remainingTicks) {
    }

    private record CooldownRow(Identifier group, ItemStack stack, int startTick, int totalTicks, int remainingTicks) {
    }
}
