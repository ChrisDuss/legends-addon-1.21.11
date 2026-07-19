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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContainerValueScanner {
    private static final BigDecimal ZERO_VALUE = BigDecimal.ZERO;
    private static final Pattern SELL_VALUE_PATTERN = Pattern.compile(
            "(?i)\\b([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+Sell\\s+Value\\b"
    );
    private static final int PAD_X = 5;
    private static final int PAD_Y = 4;
    private static final int LINE_GAP = 2;
    private static final int EDGE_GAP = 8;
    private static final String ANCHOR_X_KEY = "labelAnchorX";
    private static final String ANCHOR_Y_KEY = "labelAnchorY";
    private static final String OFFSET_X_KEY = "labelOffsetX";
    private static final String OFFSET_Y_KEY = "labelOffsetY";
    private static final String ANCHOR_LEFT = "left";
    private static final String ANCHOR_RIGHT = "right";
    private static final String ANCHOR_TOP = "container_top";
    private static final String ANCHOR_BOTTOM = "container_bottom";
    private static final String LEGACY_ANCHOR_TOP = "top";
    private static final String LEGACY_ANCHOR_BOTTOM = "bottom";

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
        int nextAnchorX = anchorXForLeft(nextLeft, box.maxWidth());
        int containerTop = accessor.legends$getY();
        int containerBottom = containerTop + accessor.legends$getBackgroundHeight();
        int anchorY = anchorYForTop(nextTop, box.contentHeight());
        boolean anchorToRight = nextAnchorX >= accessor.legends$getX() + accessor.legends$getBackgroundWidth() / 2;
        boolean anchorToBottom = Math.abs(anchorY - containerBottom) < Math.abs(anchorY - containerTop);
        int horizontalReference = anchorToRight
                ? accessor.legends$getX() + accessor.legends$getBackgroundWidth()
                : accessor.legends$getX();
        int verticalReference = anchorToBottom
                ? containerBottom
                : containerTop;

        WidgetConfigManager.setString(
                ContainerValueWidget.WIDGET_NAME,
                ANCHOR_X_KEY,
                anchorToRight ? ANCHOR_RIGHT : ANCHOR_LEFT,
                false
        );
        WidgetConfigManager.setString(
                ContainerValueWidget.WIDGET_NAME,
                ANCHOR_Y_KEY,
                anchorToBottom ? ANCHOR_BOTTOM : ANCHOR_TOP,
                false
        );
        WidgetConfigManager.setFloat(
                ContainerValueWidget.WIDGET_NAME,
                OFFSET_X_KEY,
                nextAnchorX - horizontalReference,
                false
        );
        WidgetConfigManager.setFloat(
                ContainerValueWidget.WIDGET_NAME,
                OFFSET_Y_KEY,
                anchorY - verticalReference,
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

        BigDecimal perItemValue = sellValueFromTooltip(tooltip);
        if (perItemValue.signum() <= 0) {
            return tooltip;
        }
        if (hasTotalSellValueLine(tooltip)) {
            return tooltip;
        }

        BigDecimal totalValue = safeMultiply(perItemValue, stack.getCount());
        if (totalValue.compareTo(perItemValue) <= 0) {
            return tooltip;
        }

        List<Text> withTotal = new ArrayList<>(tooltip);
        withTotal.add(Text.literal(formatCoins(totalValue) + " Total Sell Value").formatted(Formatting.GOLD));
        return List.copyOf(withTotal);
    }

    public static BigDecimal stackSellValue(MinecraftClient client, ItemStack stack) {
        if (client == null || stack == null || stack.isEmpty()) {
            return ZERO_VALUE;
        }

        BigDecimal perItemValue = sellValueFromTooltip(Screen.getTooltipFromItem(client, stack));
        if (perItemValue.signum() <= 0) {
            return ZERO_VALUE;
        }
        return safeMultiply(perItemValue, stack.getCount());
    }

    private static ValueTotals scan(HandledScreen<?> screen, MinecraftClient client) {
        BigDecimal containerValue = ZERO_VALUE;
        BigDecimal inventoryValue = ZERO_VALUE;
        boolean hasContainerSlots = false;

        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot == null || !slot.hasStack()) {
                continue;
            }

            boolean playerInventory = slot.inventory instanceof PlayerInventory;
            BigDecimal value = stackSellValue(client, slot.getStack());
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
        int screenHeight = client.getWindow().getScaledHeight();
        int anchorX = labelAnchorX(accessor, maxWidth);
        int left = leftForAnchorX(anchorX, maxWidth);
        int anchorY = labelAnchorY(accessor, contentHeight, screenHeight);
        int top = topForAnchorY(anchorY, contentHeight);

        left = clampLeft(left, maxWidth);
        top = clampTop(top, contentHeight);
        return new OverlayBox(lines, left, top, maxWidth, contentHeight);
    }

    private static int labelAnchorX(HandledScreenAccessor accessor, int maxWidth) {
        int containerLeft = accessor.legends$getX();
        int containerRight = containerLeft + accessor.legends$getBackgroundWidth();
        String anchor = WidgetConfigManager.getString(ContainerValueWidget.WIDGET_NAME, ANCHOR_X_KEY, "");
        float saved = WidgetConfigManager.getFloat(ContainerValueWidget.WIDGET_NAME, OFFSET_X_KEY, Float.NaN);
        if (Float.isFinite(saved)) {
            if (ANCHOR_RIGHT.equals(anchor)) {
                return containerRight + Math.round(saved);
            }
            if (ANCHOR_LEFT.equals(anchor)) {
                return containerLeft + Math.round(saved);
            }

            return containerLeft + Math.round(saved);
        }

        int leftOutside = containerLeft - maxWidth - PAD_X - EDGE_GAP;
        if (leftOutside >= PAD_X) {
            return anchorXForLeft(leftOutside, maxWidth);
        }

        return anchorXForLeft(containerRight + PAD_X + EDGE_GAP, maxWidth);
    }

    private static int labelAnchorY(HandledScreenAccessor accessor, int contentHeight, int screenHeight) {
        int containerTop = accessor.legends$getY();
        int containerBottom = containerTop + accessor.legends$getBackgroundHeight();
        String anchor = WidgetConfigManager.getString(ContainerValueWidget.WIDGET_NAME, ANCHOR_Y_KEY, "");
        float saved = WidgetConfigManager.getFloat(ContainerValueWidget.WIDGET_NAME, OFFSET_Y_KEY, Float.NaN);
        if (Float.isFinite(saved)) {
            if (ANCHOR_BOTTOM.equals(anchor)) {
                return containerBottom + Math.round(saved);
            }
            if (ANCHOR_TOP.equals(anchor)) {
                return containerTop + Math.round(saved);
            }
            if (LEGACY_ANCHOR_BOTTOM.equals(anchor)) {
                return containerTop + Math.round(saved);
            }
            if (LEGACY_ANCHOR_TOP.equals(anchor)) {
                return containerBottom + Math.round(saved);
            }

            return containerTop + Math.round(saved);
        }

        int spaceBelowTopEdge = screenHeight - containerTop - PAD_Y;
        if (spaceBelowTopEdge >= contentHeight) {
            return containerTop;
        }
        return containerBottom;
    }

    private static int topForAnchorY(int anchorY, int contentHeight) {
        return switch (ContainerValueWidget.growthDirection()) {
            case ContainerValueWidget.GROWTH_DIRECTION_UP -> anchorY - contentHeight;
            case ContainerValueWidget.GROWTH_DIRECTION_CENTER -> anchorY - contentHeight / 2;
            default -> anchorY;
        };
    }

    private static int anchorYForTop(int top, int contentHeight) {
        return switch (ContainerValueWidget.growthDirection()) {
            case ContainerValueWidget.GROWTH_DIRECTION_UP -> top + contentHeight;
            case ContainerValueWidget.GROWTH_DIRECTION_CENTER -> top + contentHeight / 2;
            default -> top;
        };
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

        if (ContainerValueWidget.shouldShowBorder()) {
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

    private static BigDecimal sellValueFromTooltip(List<Text> tooltip) {
        for (Text line : tooltip) {
            BigDecimal value = parseSellValue(line == null ? "" : line.getString());
            if (value.signum() > 0) {
                return value;
            }
        }
        return ZERO_VALUE;
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

    private static BigDecimal parseSellValue(String text) {
        if (text == null || text.isBlank()) {
            return ZERO_VALUE;
        }

        Matcher matcher = SELL_VALUE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return ZERO_VALUE;
        }

        try {
            return new BigDecimal(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
            return ZERO_VALUE;
        }
    }

    private static String formatCoins(BigDecimal value) {
        BigDecimal normalized = value == null ? ZERO_VALUE : value.max(ZERO_VALUE).stripTrailingZeros();
        String plain = normalized.toPlainString();
        int decimalIndex = plain.indexOf('.');
        String integerPart = decimalIndex >= 0 ? plain.substring(0, decimalIndex) : plain;
        String decimalPart = decimalIndex >= 0 ? plain.substring(decimalIndex) : "";
        return groupIntegerDigits(integerPart) + decimalPart;
    }

    private static String groupIntegerDigits(String digits) {
        StringBuilder grouped = new StringBuilder(digits.length() + digits.length() / 3);
        int firstGroupLength = digits.length() % 3;
        if (firstGroupLength == 0) {
            firstGroupLength = 3;
        }

        grouped.append(digits, 0, firstGroupLength);
        for (int i = firstGroupLength; i < digits.length(); i += 3) {
            grouped.append(',').append(digits, i, i + 3);
        }
        return grouped.toString();
    }

    private static BigDecimal safeAdd(BigDecimal left, BigDecimal right) {
        return left.add(right);
    }

    private static BigDecimal safeMultiply(BigDecimal value, int count) {
        if (value.signum() <= 0 || count <= 0) {
            return ZERO_VALUE;
        }
        return value.multiply(BigDecimal.valueOf(count));
    }

    private static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.drawVerticalLine(x, y, y + height, color);
        context.drawVerticalLine(x + width, y, y + height, color);
        context.drawHorizontalLine(x, x + width, y, color);
        context.drawHorizontalLine(x, x + width, y + height, color);
    }

    private record ValueTotals(
            BigDecimal containerValue,
            BigDecimal inventoryValue,
            boolean hasContainerSlots
    ) {
        private boolean hasAnySellValue() {
            return containerValue.signum() > 0 || inventoryValue.signum() > 0;
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
