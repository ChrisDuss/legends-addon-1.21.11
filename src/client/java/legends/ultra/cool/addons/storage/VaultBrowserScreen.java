package legends.ultra.cool.addons.storage;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public final class VaultBrowserScreen extends Screen {
    private static final int OUTER_MARGIN = 20;
    private static final int PANEL_GAP_X = 12;
    private static final int PANEL_GAP_Y = 14;
    private static final int PANEL_PADDING = 8;
    private static final int SLOT_SIZE = 18;
    private static final int PANEL_HEADER_HEIGHT = 18;
    private static final int SCREEN_HEADER_HEIGHT = 42;
    private static final int SCREEN_FOOTER_HEIGHT = 18;
    private static final Text FOOTER_HINT = Text.literal("Left-click opens | Right-click renames");
    private static final int MAX_COLUMNS = 4;
    private static final int MIN_PANEL_WIDTH = (SLOT_SIZE * 9) + (PANEL_PADDING * 2);
    private static final int MAX_PANEL_HEIGHT = PANEL_HEADER_HEIGHT + PANEL_PADDING + (6 * SLOT_SIZE) + PANEL_PADDING;

    private int scroll;
    private int maxScroll;
    private ButtonWidget profileLabelButton;
    private ButtonWidget hintLabelButton;

    public VaultBrowserScreen() {
        super(Text.literal("Vault Browser"));
    }

    @Override
    protected void init() {
        int selectorCenterX = this.width / 2;
        this.profileLabelButton = ButtonWidget.builder(Text.literal(VaultStorageManager.getSelectedProfileLabel()), button -> {
                })
                .dimensions(selectorCenterX - 60, 10, 120, 20)
                .build();
        this.profileLabelButton.active = false;
        this.hintLabelButton = ButtonWidget.builder(FOOTER_HINT, button -> {
                })
                .dimensions(20, this.height - 26, this.textRenderer.getWidth(FOOTER_HINT) + 16, 20)
                .build();
        this.hintLabelButton.active = false;

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                .dimensions(this.width - 90, 10, 70, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Reload Range"), button -> VaultStorageManager.reloadCurrentRange(this.client))
                .dimensions(20, 10, 96, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Clear Range"), button -> {
                    VaultStorageManager.clearSnapshots();
                    refreshScrollBounds();
                })
                .dimensions(122, 10, 90, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> {
                    if (VaultStorageManager.selectPreviousProfile()) {
                        this.scroll = 0;
                        updateProfileLabel();
                        refreshScrollBounds();
                    }
                })
                .dimensions(selectorCenterX - 88, 10, 20, 20)
                .build());

        addDrawableChild(this.profileLabelButton);

        addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> {
                    if (VaultStorageManager.selectNextProfile()) {
                        this.scroll = 0;
                        updateProfileLabel();
                        refreshScrollBounds();
                    }
                })
                .dimensions(selectorCenterX + 68, 10, 20, 20)
                .build());

        if (VaultStorageManager.shouldShowBrowserHint()) {
            addDrawableChild(this.hintLabelButton);
        }

        refreshScrollBounds();
    }

    @Override
    public void close() {
        MinecraftClient client = this.client;
        if (client != null) {
            client.setScreen(null);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        this.scroll = MathHelper.clamp(this.scroll - (int) Math.round(verticalAmount * 24.0D), 0, this.maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) {
            return true;
        }

        VaultPanel clickedPanel = findVaultPanelAt((int) click.x(), (int) click.y());
        if (clickedPanel == null) {
            return false;
        }

        if (click.button() == 1) {
            if (this.client != null) {
                this.client.setScreen(new VaultRenameScreen(this, clickedPanel.snapshot().vaultNumber()));
                return true;
            }
            return false;
        }

        if (click.button() != 0) {
            return false;
        }

        return VaultStorageManager.openVault(this.client, clickedPanel.snapshot().vaultNumber());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fillGradient(0, 0, this.width, this.height, 0xF0181018, 0xF0080808);
        context.drawTextWithShadow(this.textRenderer, this.title, 20, 24, 0xFFFFFF);

        List<VaultStorageManager.VaultSnapshot> snapshots = VaultStorageManager.getSnapshots();
        if (snapshots.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No vaults cached in range " + VaultStorageManager.getRangeLabel() + "."), this.width / 2, this.height / 2 - 10, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Use the profile arrows, then run Load All."), this.width / 2, this.height / 2 + 6, 0xB8B8B8);
            super.render(context, mouseX, mouseY, deltaTicks);
            return;
        }

        int columns = getColumnCount();
        int panelWidth = MIN_PANEL_WIDTH;
        int totalWidth = columns * panelWidth + (columns - 1) * PANEL_GAP_X;
        int startX = Math.max(OUTER_MARGIN, (this.width - totalWidth) / 2);
        int baseY = SCREEN_HEADER_HEIGHT - this.scroll;
        VaultPanel hoveredPanel = findVaultPanelAt(mouseX, mouseY);

        ItemStack hoveredStack = ItemStack.EMPTY;
        List<Text> hoveredTooltip = List.of();

        int x = startX;
        int y = baseY;
        int columnIndex = 0;
        int rowMaxHeight = 0;

        for (VaultStorageManager.VaultSnapshot snapshot : snapshots) {
            int panelHeight = getPanelHeight(snapshot);
            rowMaxHeight = Math.max(rowMaxHeight, panelHeight);

            if (isVisible(y, panelHeight)) {
                boolean highlighted = hoveredPanel != null && hoveredPanel.snapshot().vaultNumber() == snapshot.vaultNumber();
                drawPanel(context, snapshot, x, y, panelWidth, highlighted);
                HoveredSlot hoveredSlot = findHoveredSlot(snapshot, x, y, mouseX, mouseY);
                if (hoveredSlot != null) {
                    hoveredStack = hoveredSlot.stack();
                    hoveredTooltip = hoveredSlot.tooltip();
                }
            }

            columnIndex++;
            if (columnIndex >= columns) {
                columnIndex = 0;
                x = startX;
                y += rowMaxHeight + PANEL_GAP_Y;
                rowMaxHeight = 0;
            } else {
                x += panelWidth + PANEL_GAP_X;
            }
        }

        if (columnIndex != 0) {
            y += rowMaxHeight;
        } else {
            y -= PANEL_GAP_Y;
        }

        int loadedCount = snapshots.size();
        String loadedLabel = "Loaded in range: " + loadedCount;
        context.drawTextWithShadow(this.textRenderer, Text.literal(loadedLabel), this.width - this.textRenderer.getWidth(loadedLabel) - 20, this.height - 16, 0xB8E0B8);

        if (!hoveredStack.isEmpty() && !hoveredTooltip.isEmpty()) {
            context.drawTooltip(this.textRenderer, hoveredTooltip, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void updateProfileLabel() {
        if (this.profileLabelButton != null) {
            this.profileLabelButton.setMessage(Text.literal(VaultStorageManager.getSelectedProfileLabel()));
        }
    }

    private void refreshScrollBounds() {
        int contentHeight = computeContentHeight();
        this.maxScroll = Math.max(0, contentHeight - (this.height - SCREEN_HEADER_HEIGHT - SCREEN_FOOTER_HEIGHT));
        this.scroll = MathHelper.clamp(this.scroll, 0, this.maxScroll);
    }

    private int computeContentHeight() {
        List<VaultStorageManager.VaultSnapshot> snapshots = VaultStorageManager.getSnapshots();
        if (snapshots.isEmpty()) {
            return 0;
        }

        int columns = getColumnCount();
        int rowMaxHeight = 0;
        int rowsHeight = 0;
        int columnIndex = 0;

        for (VaultStorageManager.VaultSnapshot snapshot : snapshots) {
            rowMaxHeight = Math.max(rowMaxHeight, getPanelHeight(snapshot));
            columnIndex++;

            if (columnIndex >= columns) {
                rowsHeight += rowMaxHeight;
                rowsHeight += PANEL_GAP_Y;
                rowMaxHeight = 0;
                columnIndex = 0;
            }
        }

        if (columnIndex != 0) {
            rowsHeight += rowMaxHeight;
        } else {
            rowsHeight -= PANEL_GAP_Y;
        }

        return Math.max(0, rowsHeight);
    }

    private int getColumnCount() {
        int usableWidth = Math.max(MIN_PANEL_WIDTH, this.width - (OUTER_MARGIN * 2));
        int columns = Math.max(1, (usableWidth + PANEL_GAP_X) / (MIN_PANEL_WIDTH + PANEL_GAP_X));
        return Math.min(MAX_COLUMNS, columns);
    }

    private int getPanelHeight(VaultStorageManager.VaultSnapshot snapshot) {
        return PANEL_HEADER_HEIGHT + PANEL_PADDING + (snapshot.rows() * SLOT_SIZE) + PANEL_PADDING;
    }

    private boolean isVisible(int panelY, int panelHeight) {
        int top = SCREEN_HEADER_HEIGHT;
        int bottom = this.height - SCREEN_FOOTER_HEIGHT;
        return panelY + panelHeight >= top && panelY <= bottom;
    }

    private void drawPanel(DrawContext context, VaultStorageManager.VaultSnapshot snapshot, int x, int y, int width, boolean highlighted) {
        int height = getPanelHeight(snapshot);
        int slotAreaY = y + PANEL_HEADER_HEIGHT;
        int topBorderColor = highlighted ? 0xFFD88C34 : 0xFF7A2A24;
        int leftBorderColor = highlighted ? 0xFFD88C34 : 0xFF7A2A24;
        int rightBorderColor = highlighted ? 0xFF7A3A12 : 0xFF3A1612;
        int bottomBorderColor = highlighted ? 0xFF7A3A12 : 0xFF3A1612;

        context.fillGradient(x, y, x + width, y + height, 0xE0201A24, 0xE0100D12);
        context.fill(x, y, x + width, y + 1, topBorderColor);
        context.fill(x, y + 1, x + 1, y + height, leftBorderColor);
        context.fill(x + width - 1, y + 1, x + width, y + height, rightBorderColor);
        context.fill(x + 1, y + height - 1, x + width, y + height, bottomBorderColor);

        boolean hasCustomName = VaultStorageManager.hasCustomName(snapshot.vaultNumber());
        boolean showVaultTag = hasCustomName || VaultStorageManager.shouldAlwaysShowVaultNumber();
        String title = fitTitle(VaultStorageManager.getDisplayName(snapshot.vaultNumber()), width - (PANEL_PADDING * 2) - (showVaultTag ? 28 : 0));
        int titleColor = hasCustomName ? 0xFFE86A5C : 0xFFFFFF;
        context.drawTextWithShadow(this.textRenderer, Text.literal(title), x + PANEL_PADDING, y + 5, titleColor);
        if (showVaultTag) {
            String vaultTag = "#" + snapshot.vaultNumber();
            int vaultTagWidth = this.textRenderer.getWidth(vaultTag);
            context.drawTextWithShadow(this.textRenderer, Text.literal(vaultTag), x + width - PANEL_PADDING - vaultTagWidth, y + 5, 0xB8B8B8);
        }

        List<ItemStack> stacks = snapshot.stacks();
        int slotBaseX = x + PANEL_PADDING;
        int slotBaseY = slotAreaY + PANEL_PADDING;

        for (int index = 0; index < stacks.size(); index++) {
            int slotX = slotBaseX + ((index % 9) * SLOT_SIZE);
            int slotY = slotBaseY + ((index / 9) * SLOT_SIZE);

            context.fill(slotX, slotY, slotX + 16, slotY + 16, 0xA0101010);

            ItemStack stack = stacks.get(index);
            if (!stack.isEmpty()) {
                context.drawItem(stack, slotX, slotY);
                context.drawStackOverlay(this.textRenderer, stack, slotX, slotY);
            }
        }
    }

    private HoveredSlot findHoveredSlot(VaultStorageManager.VaultSnapshot snapshot, int x, int y, int mouseX, int mouseY) {
        int slotBaseX = x + PANEL_PADDING;
        int slotBaseY = y + PANEL_HEADER_HEIGHT + PANEL_PADDING;

        for (int index = 0; index < snapshot.stacks().size(); index++) {
            ItemStack stack = snapshot.stacks().get(index);
            if (stack.isEmpty()) {
                continue;
            }

            int slotX = slotBaseX + ((index % 9) * SLOT_SIZE);
            int slotY = slotBaseY + ((index / 9) * SLOT_SIZE);

            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                return new HoveredSlot(stack, buildTooltip(stack));
            }
        }

        return null;
    }

    private List<Text> buildTooltip(ItemStack stack) {
        List<Text> tooltip = new ArrayList<>();
        tooltip.add(stack.getName());
        tooltip.addAll(VaultStorageManager.getLoreLines(stack));
        return tooltip;
    }

    private String fitTitle(String title, int maxWidth) {
        if (title == null || title.isBlank() || this.textRenderer.getWidth(title) <= maxWidth) {
            return title;
        }

        String trimmed = title;
        while (trimmed.length() > 1 && this.textRenderer.getWidth(trimmed + "...") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed + "...";
    }

    private VaultPanel findVaultPanelAt(int mouseX, int mouseY) {
        List<VaultStorageManager.VaultSnapshot> snapshots = VaultStorageManager.getSnapshots();
        if (snapshots.isEmpty()) {
            return null;
        }

        int columns = getColumnCount();
        int panelWidth = MIN_PANEL_WIDTH;
        int totalWidth = columns * panelWidth + (columns - 1) * PANEL_GAP_X;
        int startX = Math.max(OUTER_MARGIN, (this.width - totalWidth) / 2);
        int baseY = SCREEN_HEADER_HEIGHT - this.scroll;

        int x = startX;
        int y = baseY;
        int columnIndex = 0;
        int rowMaxHeight = 0;

        for (VaultStorageManager.VaultSnapshot snapshot : snapshots) {
            int panelHeight = getPanelHeight(snapshot);
            rowMaxHeight = Math.max(rowMaxHeight, panelHeight);

            if (mouseX >= x && mouseX < x + panelWidth && mouseY >= y && mouseY < y + panelHeight) {
                return new VaultPanel(snapshot);
            }

            columnIndex++;
            if (columnIndex >= columns) {
                columnIndex = 0;
                x = startX;
                y += rowMaxHeight + PANEL_GAP_Y;
                rowMaxHeight = 0;
            } else {
                x += panelWidth + PANEL_GAP_X;
            }
        }

        return null;
    }

    private record HoveredSlot(ItemStack stack, List<Text> tooltip) {
    }

    private record VaultPanel(VaultStorageManager.VaultSnapshot snapshot) {
    }
}
