package legends.ultra.cool.addons.util;

import legends.ultra.cool.addons.hud.widget.otherTypes.ContainerValueWidget;
import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.mixin.client.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContainerValueScanner {
    private static final Pattern SELL_VALUE_PATTERN = Pattern.compile(
            "(?i)\\b([0-9][0-9,]*)\\s+Sell\\s+Value\\b"
    );
    private static final int PAD_X = 5;
    private static final int PAD_Y = 4;
    private static final int LINE_GAP = 2;
    private static final String OFFSET_X_KEY = "labelOffsetX";
    private static final String OFFSET_Y_KEY = "labelOffsetY";

    private static boolean dragging;
    private static double dragOffsetX;
    private static double dragOffsetY;

    private ContainerValueScanner() {
    }

    public static void render(HandledScreen<?> screen, DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        OverlayBox box = currentOverlayBox(screen, client);
        if (box == null) {
            return;
        }

        drawLines(context, client.textRenderer, box.lines(), box.left(), box.top());
    }

    public static boolean mouseClicked(HandledScreen<?> screen, double mouseX, double mouseY, int button) {
        if (button != 0 || !ContainerValueWidget.shouldDragLabel()) {
            return false;
        }

        OverlayBox box = currentOverlayBox(screen, MinecraftClient.getInstance());
        if (box == null || !box.contains(mouseX, mouseY)) {
            return false;
        }

        dragging = true;
        dragOffsetX = mouseX - box.left();
        dragOffsetY = mouseY - box.top();
        return true;
    }

    public static boolean mouseDragged(HandledScreen<?> screen, double mouseX, double mouseY, int button) {
        if (!dragging || button != 0 || !ContainerValueWidget.shouldDragLabel()) {
            return false;
        }

        OverlayBox box = currentOverlayBox(screen, MinecraftClient.getInstance());
        if (box == null) {
            return false;
        }

        HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
        int nextLeft = clampLeft((int) Math.round(mouseX - dragOffsetX), box.maxWidth());
        int nextTop = clampTop((int) Math.round(mouseY - dragOffsetY), box.contentHeight());
        WidgetConfigManager.setFloat(
                ContainerValueWidget.WIDGET_NAME,
                OFFSET_X_KEY,
                anchorXForLeft(nextLeft, box.maxWidth()) - accessor.legends$getX(),
                false
        );
        WidgetConfigManager.setFloat(
                ContainerValueWidget.WIDGET_NAME,
                OFFSET_Y_KEY,
                nextTop - accessor.legends$getY(),
                false
        );
        return true;
    }

    public static boolean mouseReleased() {
        if (!dragging) {
            return false;
        }

        dragging = false;
        WidgetConfigManager.save();
        return true;
    }

    public static List<Text> withTotalSellValue(MinecraftClient client, ItemStack stack, List<Text> tooltip) {
        if (client == null) {
            return tooltip;
        }
        return withTotalSellValue(stack, tooltip);
    }

    public static List<Text> withTotalSellValue(ItemStack stack, List<Text> tooltip) {
        if (!ContainerValueWidget.shouldShowStackTooltipTotal()
                || stack == null
                || stack.isEmpty()
                || stack.getCount() <= 1
                || tooltip == null
                || tooltip.isEmpty()) {
            return tooltip;
        }

        long perItemValue = sellValueFromTooltip(tooltip);
        if (perItemValue <= 0L) {
            return tooltip;
        }
        if (hasTotalSellValueLine(tooltip)) {
            return tooltip;
        }

        long totalValue = safeMultiply(perItemValue, stack.getCount());
        if (totalValue <= perItemValue) {
            return tooltip;
        }

        List<Text> withTotal = new ArrayList<>(tooltip);
        withTotal.add(Text.literal(formatCoins(totalValue) + " Total Sell Value").formatted(Formatting.GOLD));
        return List.copyOf(withTotal);
    }

    public static long stackSellValue(MinecraftClient client, ItemStack stack) {
        if (client == null || stack == null || stack.isEmpty()) {
            return 0L;
        }

        long perItemValue = sellValueFromTooltip(Screen.getTooltipFromItem(client, stack));
        if (perItemValue <= 0L) {
            return 0L;
        }
        return safeMultiply(perItemValue, stack.getCount());
    }

    private static ValueTotals scan(HandledScreen<?> screen, MinecraftClient client) {
        long containerValue = 0L;
        long inventoryValue = 0L;
        boolean hasContainerSlots = false;

        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot == null || !slot.hasStack()) {
                continue;
            }

            boolean playerInventory = slot.inventory instanceof PlayerInventory;
            long value = stackSellValue(client, slot.getStack());
            if (playerInventory) {
                inventoryValue = safeAdd(inventoryValue, value);
            } else {
                hasContainerSlots = true;
                containerValue = safeAdd(containerValue, value);
            }
        }

        return new ValueTotals(containerValue, inventoryValue, hasContainerSlots);
    }

    private static List<String> overlayLines(ValueTotals totals) {
        List<String> lines = new ArrayList<>();
        if (totals.hasContainerSlots()) {
            lines.add("Container Value: " + formatCoins(totals.containerValue()));
                lines.add("Inventory Value: " + formatCoins(totals.inventoryValue()));
                lines.add("Total Value: " + formatCoins(safeAdd(totals.containerValue(), totals.inventoryValue())));

        } else {
            lines.add("Inventory Value: " + formatCoins(totals.inventoryValue()));
        }
        return List.copyOf(lines);
    }

    private static OverlayBox currentOverlayBox(HandledScreen<?> screen, MinecraftClient client) {
        if (!ContainerValueWidget.isEnabledGlobal()
                || client == null
                || client.player == null
                || !AddonServerGate.shouldRunOnCurrentServer()
                || UiVisibility.isHudHidden()) {
            return null;
        }

        ValueTotals totals = scan(screen, client);
        boolean dragMode = ContainerValueWidget.shouldDragLabel();
        if (!totals.hasAnySellValue() && !dragMode) {
            return null;
        }

        List<String> lines = overlayLines(totals);
        if (lines.isEmpty()) {
            return null;
        }

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, client.textRenderer.getWidth(line));
        }

        int lineHeight = client.textRenderer.fontHeight;
        int contentHeight = lines.size() * lineHeight + Math.max(0, lines.size() - 1) * LINE_GAP;
        HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
        int anchorX = accessor.legends$getX() + labelAnchorOffsetX(maxWidth);
        int left = leftForAnchorX(anchorX, maxWidth);
        int top = accessor.legends$getY() + labelOffsetY();
        return new OverlayBox(lines, left, top, maxWidth, contentHeight);
    }

    private static int labelAnchorOffsetX(int maxWidth) {
        float saved = WidgetConfigManager.getFloat(ContainerValueWidget.WIDGET_NAME, OFFSET_X_KEY, Float.NaN);
        if (Float.isFinite(saved)) {
            return Math.round(saved);
        }
        return switch (ContainerValueWidget.textAlignment()) {
            case ContainerValueWidget.TEXT_ALIGN_CENTER -> 4 - PAD_X * 2 - maxWidth / 2;
            case ContainerValueWidget.TEXT_ALIGN_RIGHT -> 4 - PAD_X * 2;
            default -> 4 - (maxWidth + PAD_X * 2);
        };
    }

    private static int labelOffsetY() {
        float saved = WidgetConfigManager.getFloat(ContainerValueWidget.WIDGET_NAME, OFFSET_Y_KEY, Float.NaN);
        if (Float.isFinite(saved)) {
            return Math.round(saved);
        }
        return 4;
    }

    private static int clampLeft(int left, int maxWidth) {
        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client == null || client.getWindow() == null ? Integer.MAX_VALUE : client.getWindow().getScaledWidth();
        int min = PAD_X;
        int max = Math.max(min, screenWidth - maxWidth - PAD_X);
        return MathHelper.clamp(left, min, max);
    }

    private static int clampTop(int top, int contentHeight) {
        MinecraftClient client = MinecraftClient.getInstance();
        int screenHeight = client == null || client.getWindow() == null ? Integer.MAX_VALUE : client.getWindow().getScaledHeight();
        int min = PAD_Y;
        int max = Math.max(min, screenHeight - contentHeight - PAD_Y);
        return MathHelper.clamp(top, min, max);
    }

    private static void drawLines(DrawContext context, TextRenderer textRenderer, List<String> lines, int left, int top) {
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, textRenderer.getWidth(line));
        }

        int lineHeight = textRenderer.fontHeight;
        int height = lines.size() * lineHeight + Math.max(0, lines.size() - 1) * LINE_GAP;
        if (ContainerValueWidget.shouldShowBackground()) {
            context.fill(
                    left - PAD_X,
                    top - PAD_Y,
                    left + maxWidth + PAD_X,
                    top + height + PAD_Y,
                    ContainerValueWidget.backgroundColor()
            );
        }

        if (ContainerValueWidget.shouldShowBorder() || ContainerValueWidget.shouldDragLabel()) {
            int borderColor = ContainerValueWidget.shouldDragLabel()
                    ? brightenAlpha(ContainerValueWidget.borderColor())
                    : ContainerValueWidget.borderColor();
            drawBorder(context, left - PAD_X, top - PAD_Y, maxWidth + PAD_X * 2, height + PAD_Y * 2, borderColor);
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            context.drawText(
                    textRenderer,
                    line,
                    alignedLineX(textRenderer, line, left, maxWidth),
                    top + i * (lineHeight + LINE_GAP) + 1,
                    ContainerValueWidget.textColor(),
                    !ContainerValueWidget.shouldShowBackground()
            );
        }
    }

    private static int alignedLineX(TextRenderer textRenderer, String line, int left, int maxWidth) {
        int lineWidth = textRenderer.getWidth(line);
        return switch (ContainerValueWidget.textAlignment()) {
            case ContainerValueWidget.TEXT_ALIGN_CENTER -> left + Math.max(0, (maxWidth - lineWidth) / 2);
            case ContainerValueWidget.TEXT_ALIGN_RIGHT -> left + Math.max(0, maxWidth - lineWidth);
            default -> left;
        };
    }

    private static int leftForAnchorX(int anchorX, int maxWidth) {
        return switch (ContainerValueWidget.textAlignment()) {
            case ContainerValueWidget.TEXT_ALIGN_CENTER -> anchorX - maxWidth / 2;
            case ContainerValueWidget.TEXT_ALIGN_RIGHT -> anchorX - maxWidth;
            default -> anchorX;
        };
    }

    private static int anchorXForLeft(int left, int maxWidth) {
        return switch (ContainerValueWidget.textAlignment()) {
            case ContainerValueWidget.TEXT_ALIGN_CENTER -> left + maxWidth / 2;
            case ContainerValueWidget.TEXT_ALIGN_RIGHT -> left + maxWidth;
            default -> left;
        };
    }

    private static int brightenAlpha(int color) {
        int alpha = Math.max(0xCC, (color >>> 24) & 0xFF);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static long sellValueFromTooltip(List<Text> tooltip) {
        for (Text line : tooltip) {
            long value = parseSellValue(line == null ? "" : line.getString());
            if (value > 0L) {
                return value;
            }
        }
        return 0L;
    }

    private static boolean hasTotalSellValueLine(List<Text> tooltip) {
        for (Text line : tooltip) {
            String text = line == null ? "" : line.getString();
            if (text.toLowerCase(Locale.ROOT).contains("total sell value")) {
                return true;
            }
        }
        return false;
    }

    private static long parseSellValue(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }

        Matcher matcher = SELL_VALUE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return 0L;
        }

        try {
            return Long.parseLong(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String formatCoins(long value) {
        return String.format(Locale.US, "%,d", Math.max(0L, value));
    }

    private static long safeAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long safeMultiply(long value, int count) {
        if (value <= 0L || count <= 0) {
            return 0L;
        }
        if (value > Long.MAX_VALUE / count) {
            return Long.MAX_VALUE;
        }
        return value * count;
    }

    private static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.drawVerticalLine(x, y, y + height, color);
        context.drawVerticalLine(x + width, y, y + height, color);
        context.drawHorizontalLine(x, x + width, y, color);
        context.drawHorizontalLine(x, x + width, y + height, color);
    }

    private record ValueTotals(
            long containerValue,
            long inventoryValue,
            boolean hasContainerSlots
    ) {
        private boolean hasAnySellValue() {
            return containerValue > 0L || inventoryValue > 0L;
        }
    }

    private record OverlayBox(List<String> lines, int left, int top, int maxWidth, int contentHeight) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= left - PAD_X
                    && mouseX <= left + maxWidth + PAD_X
                    && mouseY >= top - PAD_Y
                    && mouseY <= top + contentHeight + PAD_Y;
        }
    }
}
