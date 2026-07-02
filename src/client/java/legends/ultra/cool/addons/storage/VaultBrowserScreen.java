package legends.ultra.cool.addons.storage;

import legends.ultra.cool.addons.hud.widget.otherTypes.VaultBrowserWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VaultBrowserScreen extends Screen {
    private static final int OUTER_MARGIN = 20;
    private static final int PANEL_GAP_X = 12;
    private static final int PANEL_GAP_Y = 14;
    private static final int PANEL_PADDING = 8;
    private static final int SLOT_SIZE = 18;
    private static final int PANEL_HEADER_HEIGHT = 18;
    private static final int SCREEN_HEADER_HEIGHT = 42;
    private static final int SCREEN_FOOTER_HEIGHT = 18;
    private static final int SEARCH_FIELD_MAX_WIDTH = 260;
    private static final int SEARCH_FIELD_MIN_WIDTH = 160;
    private static final int SEARCH_FIELD_HEIGHT = 20;
    private static final int SEARCH_FIELD_BOTTOM_MARGIN = 4;
    private static final int SLOT_RENDER_SIZE = 16;
    private static final int LOCKED_DETAILS_HEIGHT = 28;
    private static final Text FOOTER_HINT = Text.literal("Left-click opens or unlocks | Right-click renames");
    private static final int MAX_COLUMNS = 4;
    private static final int MIN_PANEL_WIDTH = (SLOT_SIZE * 9) + (PANEL_PADDING * 2);

    private int scroll;
    private int maxScroll;
    private ButtonWidget profileLabelButton;
    private TextFieldWidget searchField;
    private String searchQuery = "";
    private final HandledScreen<?> storageMenuScreen;

    public VaultBrowserScreen(HandledScreen<?> storageMenuScreen) {
        super(Text.literal("Vault Browser"));
        this.storageMenuScreen = storageMenuScreen;
    }

    @Override
    protected void init() {
        int selectorCenterX = this.width / 2;
        this.profileLabelButton = ButtonWidget.builder(Text.literal(VaultStorageManager.getSelectedProfileLabel()), button -> {
                })
                .dimensions(selectorCenterX - 60, 10, 120, 20)
                .build();
        this.profileLabelButton.active = false;

        addDrawableChild(ButtonWidget.builder(Text.literal("Storage Menu"), button -> close())
                .dimensions(this.width - 116, 10, 96, 20)
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

        int searchWidth = getSearchFieldWidth();
        this.searchField = new TextFieldWidget(
                this.textRenderer,
                (this.width - searchWidth) / 2,
                getSearchFieldY(),
                searchWidth,
                SEARCH_FIELD_HEIGHT,
                Text.literal("Search")
        );
        this.searchField.setMaxLength(80);
        this.searchField.setPlaceholder(Text.literal("Search items"));
        this.searchField.setText(this.searchQuery);
        this.searchField.setChangedListener(value -> {
            this.searchQuery = value == null ? "" : value;
            refreshScrollBounds();
        });
        addDrawableChild(this.searchField);

        refreshScrollBounds();
    }

    @Override
    public void close() {
        VaultStorageManager.closeBrowserOverlay(this.storageMenuScreen);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_F && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            focusSearchField();
            return true;
        }

        if (isSearchFocused()) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                if (!this.searchField.getText().isBlank()) {
                    this.searchField.setText("");
                    return true;
                }

                this.searchField.setFocused(false);
                return true;
            }

            if (this.searchField.keyPressed(input)) {
                return true;
            }

            writeSearchCharacter(input);
            return true;
        }

        return false;
    }

    public boolean capturesKeyboard() {
        return isSearchFocused();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        this.scroll = MathHelper.clamp(this.scroll - (int) Math.round(verticalAmount * getScrollStep()), 0, this.maxScroll);
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

        if (click.button() == 1 && !clickedPanel.entry().locked()) {
            if (this.client != null) {
                VaultStorageManager.requestBrowserOverlay();
                this.client.setScreen(new VaultRenameScreen(this.storageMenuScreen, clickedPanel.entry().vaultNumber()));
                return true;
            }
            return false;
        }

        if (click.button() != 0) {
            return false;
        }

        if (clickedPanel.entry().locked()) return VaultStorageManager.clickStorageMenuVault(this.client, this.storageMenuScreen, clickedPanel.entry().vaultNumber());
        else return VaultStorageManager.openVault(client, clickedPanel.entry().vaultNumber());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        refreshScrollBounds();
        context.fillGradient(0, 0, this.width, this.height, 0xF0181018, 0xF0080808);
        context.drawTextWithShadow(this.textRenderer, this.title, 20, 24, 0xFFFFFF);

        List<VaultStorageManager.VaultBrowserEntry> entries = VaultStorageManager.getBrowserEntries();
        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No vaults cached in range " + VaultStorageManager.getRangeLabel() + "."), this.width / 2, this.height / 2 - 10, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Open /ec to discover locked vaults, or run Load All."), this.width / 2, this.height / 2 + 6, 0xB8B8B8);
            super.render(context, mouseX, mouseY, deltaTicks);
            return;
        }

        int columns = getColumnCount();
        int panelWidth = getPanelWidth();
        int totalWidth = columns * panelWidth + (columns - 1) * getPanelGapX();
        int startX = Math.max(getOuterMargin(), (this.width - totalWidth) / 2);
        int baseY = SCREEN_HEADER_HEIGHT - this.scroll;
        VaultPanel hoveredPanel = findVaultPanelAt(mouseX, mouseY);

        ItemStack hoveredStack = ItemStack.EMPTY;
        List<Text> hoveredTooltip = List.of();

        int x = startX;
        int y = baseY;
        int columnIndex = 0;
        int rowMaxHeight = 0;

        for (VaultStorageManager.VaultBrowserEntry entry : entries) {
            int panelHeight = getPanelHeight(entry);
            rowMaxHeight = Math.max(rowMaxHeight, panelHeight);

            if (isVisible(y, panelHeight)) {
                boolean highlighted = hoveredPanel != null && hoveredPanel.entry().vaultNumber() == entry.vaultNumber();
                drawPanel(context, entry, x, y, highlighted, entryHasSearchMatch(entry));
                HoveredSlot hoveredSlot = findHoveredSlot(entry, x, y, mouseX, mouseY);
                if (hoveredSlot != null) {
                    hoveredStack = hoveredSlot.stack();
                    hoveredTooltip = hoveredSlot.tooltip();
                }
            }

            columnIndex++;
            if (columnIndex >= columns) {
                columnIndex = 0;
                x = startX;
                y += rowMaxHeight + getPanelGapY();
                rowMaxHeight = 0;
            } else {
                x += panelWidth + getPanelGapX();
            }
        }

        if (columnIndex != 0) {
            y += rowMaxHeight;
        } else {
            y -= getPanelGapY();
        }

        int loadedCount = VaultStorageManager.getSnapshots().size();
        long lockedCount = entries.stream().filter(VaultStorageManager.VaultBrowserEntry::locked).count();
        String loadedLabel = isSearchActive()
                ? "Matches: " + countMatchingItemSlots(entries) + " | Loaded: " + loadedCount + " | Locked: " + lockedCount
                : "Loaded: " + loadedCount + " | Locked: " + lockedCount;
        loadedLabel = fitTitle(loadedLabel, Math.max(1, this.width - 40));
        context.drawTextWithShadow(this.textRenderer, Text.literal(loadedLabel), this.width - this.textRenderer.getWidth(loadedLabel) - 20, this.height - SCREEN_FOOTER_HEIGHT + 10, 0xB8E0B8);

        super.render(context, mouseX, mouseY, deltaTicks);

        if (!hoveredStack.isEmpty() && !hoveredTooltip.isEmpty()) {
            drawItemTooltipImmediately(context, hoveredStack, mouseX, mouseY);
        }

        if (VaultStorageManager.shouldShowBrowserHint()) context.drawTextWithShadow(this.textRenderer, FOOTER_HINT, 20, this.height - SCREEN_FOOTER_HEIGHT + 4, 0xFFFFFFFF);
    }

    private void drawItemTooltipImmediately(
            DrawContext context,
            ItemStack stack,
            int mouseX,
            int mouseY
    ) {
        if (this.client == null) {
            return;
        }

        List<TooltipComponent> components = new ArrayList<>();
        for (Text line : Screen.getTooltipFromItem(this.client, stack)) {
            components.add(TooltipComponent.of(line.asOrderedText()));
        }
        stack.getTooltipData().ifPresent(data ->
                components.add(components.isEmpty() ? 0 : 1, TooltipComponent.of(data)));

        context.drawTooltipImmediately(
                this.textRenderer,
                components,
                mouseX,
                mouseY,
                HoveredTooltipPositioner.INSTANCE,
                stack.get(DataComponentTypes.TOOLTIP_STYLE)
        );
    }

    private void updateProfileLabel() {
        if (this.profileLabelButton != null) {
            this.profileLabelButton.setMessage(Text.literal(VaultStorageManager.getSelectedProfileLabel()));
        }
    }

    private void focusSearchField() {
        if (this.searchField == null) {
            return;
        }

        setFocused(this.searchField);
        this.searchField.setFocused(true);
    }

    private boolean isSearchFocused() {
        return this.searchField != null && this.searchField.isFocused();
    }

    private void writeSearchCharacter(KeyInput input) {
        String character = searchCharacterForKey(input);
        if (!character.isEmpty()) {
            this.searchField.write(character);
        }
    }

    private String searchCharacterForKey(KeyInput input) {
        if (hasTextModifier(input)) {
            return "";
        }

        int key = input.key();
        boolean shifted = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;

        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            char character = (char) ('a' + (key - GLFW.GLFW_KEY_A));
            return String.valueOf(shifted ? Character.toUpperCase(character) : character);
        }

        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            String normal = "0123456789";
            String shiftedNumbers = ")!@#$%^&*(";
            int index = key - GLFW.GLFW_KEY_0;
            return String.valueOf((shifted ? shiftedNumbers : normal).charAt(index));
        }

        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            return String.valueOf((char) ('0' + (key - GLFW.GLFW_KEY_KP_0)));
        }

        switch (key) {
            case GLFW.GLFW_KEY_SPACE:
                return " ";
            case GLFW.GLFW_KEY_MINUS:
                return shifted ? "_" : "-";
            case GLFW.GLFW_KEY_EQUAL:
                return shifted ? "+" : "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET:
                return shifted ? "{" : "[";
            case GLFW.GLFW_KEY_RIGHT_BRACKET:
                return shifted ? "}" : "]";
            case GLFW.GLFW_KEY_BACKSLASH:
                return shifted ? "|" : "\\";
            case GLFW.GLFW_KEY_SEMICOLON:
                return shifted ? ":" : ";";
            case GLFW.GLFW_KEY_APOSTROPHE:
                return shifted ? "\"" : "'";
            case GLFW.GLFW_KEY_GRAVE_ACCENT:
                return shifted ? "~" : "`";
            case GLFW.GLFW_KEY_COMMA:
                return shifted ? "<" : ",";
            case GLFW.GLFW_KEY_PERIOD:
                return shifted ? ">" : ".";
            case GLFW.GLFW_KEY_SLASH:
                return shifted ? "?" : "/";
            case GLFW.GLFW_KEY_KP_DECIMAL:
                return ".";
            case GLFW.GLFW_KEY_KP_DIVIDE:
                return "/";
            case GLFW.GLFW_KEY_KP_MULTIPLY:
                return "*";
            case GLFW.GLFW_KEY_KP_SUBTRACT:
                return "-";
            case GLFW.GLFW_KEY_KP_ADD:
                return "+";
            default:
                return "";
        }
    }

    private boolean hasTextModifier(KeyInput input) {
        int modifiers = input.modifiers();
        int textModifiers = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;
        return (modifiers & textModifiers) != 0;
    }

    private int getSearchFieldWidth() {
        int availableWidth = Math.max(80, this.width - 40);
        int desiredWidth = Math.min(SEARCH_FIELD_MAX_WIDTH, Math.max(SEARCH_FIELD_MIN_WIDTH, this.width - 360));
        return Math.min(availableWidth, desiredWidth);
    }

    private int getSearchFieldY() {
        return this.height - SEARCH_FIELD_BOTTOM_MARGIN - SEARCH_FIELD_HEIGHT;
    }

    private String getSearchQuery() {
        return this.searchField == null ? this.searchQuery : this.searchField.getText();
    }

    private boolean isSearchActive() {
        return !normalizeSearchText(getSearchQuery()).isBlank();
    }

    private int countMatchingItemSlots(List<VaultStorageManager.VaultBrowserEntry> entries) {
        if (!isSearchActive()) {
            return 0;
        }

        int matches = 0;
        for (VaultStorageManager.VaultBrowserEntry entry : entries) {
            if (entry.locked() || entry.snapshot() == null) {
                if (stackMatchesSearch(entry.menuIcon())) {
                    matches++;
                }
                continue;
            }

            for (ItemStack stack : entry.snapshot().stacks()) {
                if (stackMatchesSearch(stack)) {
                    matches++;
                }
            }
        }
        return matches;
    }

    private boolean entryHasSearchMatch(VaultStorageManager.VaultBrowserEntry entry) {
        if (!isSearchActive()) {
            return false;
        }

        if (entry.locked() || entry.snapshot() == null) {
            return stackMatchesSearch(entry.menuIcon());
        }

        for (ItemStack stack : entry.snapshot().stacks()) {
            if (stackMatchesSearch(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean stackMatchesSearch(ItemStack stack) {
        String query = normalizeSearchText(getSearchQuery());
        if (query.isBlank() || stack == null || stack.isEmpty()) {
            return false;
        }

        String searchable = stackSearchableText(stack);
        for (String term : query.split("\\s+")) {
            if (!term.isBlank() && !searchable.contains(term)) {
                return false;
            }
        }
        return true;
    }

    private String stackSearchableText(ItemStack stack) {
        StringBuilder builder = new StringBuilder();
        if (stack == null || stack.isEmpty()) {
            return "";
        }

        appendSearchValue(builder, stack.getName().getString());
        appendSearchValue(builder, stack.getItem().toString());
        for (Text loreLine : VaultStorageManager.getLoreLines(stack)) {
            appendSearchValue(builder, loreLine.getString());
        }
        return builder.toString();
    }

    private void appendSearchValue(StringBuilder builder, String value) {
        String normalized = normalizeSearchText(value);
        if (normalized.isBlank()) {
            return;
        }

        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(normalized);
    }

    private static String normalizeSearchText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
    }

    private float getPanelScale() {
        return Math.max(0.25f, VaultBrowserWidget.getScaleSetting());
    }

    private int scalePanelMetric(int value) {
        return Math.max(1, Math.round(value * getPanelScale()));
    }

    private int getScrollStep() {
        return Math.max(24, scalePanelMetric(24));
    }

    private int getOuterMargin() {
        return scalePanelMetric(OUTER_MARGIN);
    }

    private int getPanelGapX() {
        return scalePanelMetric(PANEL_GAP_X);
    }

    private int getPanelGapY() {
        return scalePanelMetric(PANEL_GAP_Y);
    }

    private int getBasePanelHeight(VaultStorageManager.VaultBrowserEntry entry) {
        if (entry.locked()) {
            return PANEL_HEADER_HEIGHT + PANEL_PADDING + LOCKED_DETAILS_HEIGHT + PANEL_PADDING;
        }
        if (entry.snapshot() == null) {
            return PANEL_HEADER_HEIGHT + PANEL_PADDING + SLOT_RENDER_SIZE + PANEL_PADDING;
        }

        return PANEL_HEADER_HEIGHT + PANEL_PADDING + (entry.snapshot().rows() * SLOT_SIZE) + PANEL_PADDING;
    }

    private int getPanelWidth() {
        return scalePanelMetric(MIN_PANEL_WIDTH);
    }

    private int getPanelHeight(VaultStorageManager.VaultBrowserEntry entry) {
        return scalePanelMetric(getBasePanelHeight(entry));
    }

    private void refreshScrollBounds() {
        int contentHeight = computeContentHeight();
        this.maxScroll = Math.max(0, contentHeight - (this.height - SCREEN_HEADER_HEIGHT - SCREEN_FOOTER_HEIGHT));
        this.scroll = MathHelper.clamp(this.scroll, 0, this.maxScroll);
    }

    private int computeContentHeight() {
        List<VaultStorageManager.VaultBrowserEntry> entries = VaultStorageManager.getBrowserEntries();
        if (entries.isEmpty()) {
            return 0;
        }

        int columns = getColumnCount();
        int rowMaxHeight = 0;
        int rowsHeight = 0;
        int columnIndex = 0;

        for (VaultStorageManager.VaultBrowserEntry entry : entries) {
            rowMaxHeight = Math.max(rowMaxHeight, getPanelHeight(entry));
            columnIndex++;

            if (columnIndex >= columns) {
                rowsHeight += rowMaxHeight;
                rowsHeight += getPanelGapY();
                rowMaxHeight = 0;
                columnIndex = 0;
            }
        }

        if (columnIndex != 0) {
            rowsHeight += rowMaxHeight;
        } else {
            rowsHeight -= getPanelGapY();
        }

        return Math.max(0, rowsHeight);
    }

    private int getColumnCount() {
        int panelWidth = getPanelWidth();
        int usableWidth = Math.max(panelWidth, this.width - (getOuterMargin() * 2));
        int columns = Math.max(1, (usableWidth + getPanelGapX()) / (panelWidth + getPanelGapX()));
        return Math.min(MAX_COLUMNS, columns);
    }

    private boolean isVisible(int panelY, int panelHeight) {
        int top = SCREEN_HEADER_HEIGHT;
        int bottom = this.height - SCREEN_FOOTER_HEIGHT;
        return panelY + panelHeight >= top && panelY <= bottom;
    }

    private void drawPanel(
            DrawContext context,
            VaultStorageManager.VaultBrowserEntry entry,
            int x,
            int y,
            boolean highlighted,
            boolean searchMatched
    ) {
        int width = MIN_PANEL_WIDTH;
        int height = getBasePanelHeight(entry);
        int topBorderColor = entry.locked()
                ? searchMatched ? 0xFFFFD84D : highlighted ? 0xFFFF7868 : 0xFF9A3C36
                : searchMatched ? 0xFFFFD84D : highlighted ? 0xFFD88C34 : 0xFF7A2A24;
        int leftBorderColor = topBorderColor;
        int rightBorderColor = entry.locked()
                ? searchMatched ? 0xFFB88920 : highlighted ? 0xFF9A4038 : 0xFF4D201D
                : searchMatched ? 0xFFB88920 : highlighted ? 0xFF7A3A12 : 0xFF3A1612;
        int bottomBorderColor = rightBorderColor;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(getPanelScale(), getPanelScale());

        context.fillGradient(
                0,
                0,
                width,
                height,
                entry.locked() ? 0xE02A1818 : 0xE0201A24,
                entry.locked() ? 0xE0140C0C : 0xE0100D12
        );
        context.fill(0, 0, width, 1, topBorderColor);
        context.fill(0, 1, 1, height, leftBorderColor);
        context.fill(width - 1, 1, width, height, rightBorderColor);
        context.fill(1, height - 1, width, height, bottomBorderColor);

        boolean hasCustomName = !entry.locked() && VaultStorageManager.hasCustomName(entry.vaultNumber());
        boolean showVaultTag = hasCustomName || VaultStorageManager.shouldAlwaysShowVaultNumber();
        String title = fitTitle(VaultStorageManager.getDisplayName(entry.vaultNumber()), width - (PANEL_PADDING * 2) - (showVaultTag ? 28 : 0));
        int titleColor = entry.locked() ? 0xFFFF8A7A : hasCustomName ? 0xFFE86A5C : 0xFFFFFF;
        context.drawTextWithShadow(this.textRenderer, Text.literal(title), PANEL_PADDING, 5, titleColor);
        if (showVaultTag) {
            String vaultTag = "#" + entry.vaultNumber();
            int vaultTagWidth = this.textRenderer.getWidth(vaultTag);
            context.drawTextWithShadow(this.textRenderer, Text.literal(vaultTag), width - PANEL_PADDING - vaultTagWidth, 5, 0xB8B8B8);
        }

        int slotBaseX = PANEL_PADDING;
        int slotBaseY = PANEL_HEADER_HEIGHT + PANEL_PADDING;

        if (entry.locked()) {
            context.fill(slotBaseX, slotBaseY, slotBaseX + SLOT_RENDER_SIZE, slotBaseY + SLOT_RENDER_SIZE, 0xA0101010);
            boolean menuIconMatched = stackMatchesSearch(entry.menuIcon());
            if (menuIconMatched) {
                fillSlotHighlight(context, slotBaseX, slotBaseY);
            }
            if (!entry.menuIcon().isEmpty()) {
                context.drawItem(entry.menuIcon(), slotBaseX, slotBaseY);
            }
            if (menuIconMatched) {
                drawSlotHighlight(context, slotBaseX, slotBaseY);
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal("Locked"), slotBaseX + SLOT_SIZE + 4, slotBaseY, 0xFFFF7868);
            String price = entry.price().isBlank() ? "Price unavailable" : entry.price();
            String fittedPrice = fitTitle(price, width - slotBaseX - SLOT_SIZE - PANEL_PADDING - 4);
            context.drawTextWithShadow(this.textRenderer, Text.literal(fittedPrice), slotBaseX + SLOT_SIZE + 4, slotBaseY + 9, 0xFF55FF55);
            if (!entry.legendLevelRequirement().isBlank()) {
                String requirement = fitTitle(entry.legendLevelRequirement(), width - (PANEL_PADDING * 2));
                context.drawTextWithShadow(this.textRenderer, Text.literal(requirement), slotBaseX, slotBaseY + 19, 0xFF55CCFF);
            }
            context.getMatrices().popMatrix();
            return;
        }

        if (entry.snapshot() == null) {
            context.fill(slotBaseX, slotBaseY, slotBaseX + SLOT_RENDER_SIZE, slotBaseY + SLOT_RENDER_SIZE, 0xA0101010);
            boolean menuIconMatched = stackMatchesSearch(entry.menuIcon());
            if (menuIconMatched) {
                fillSlotHighlight(context, slotBaseX, slotBaseY);
            }
            if (!entry.menuIcon().isEmpty()) {
                context.drawItem(entry.menuIcon(), slotBaseX, slotBaseY);
            }
            if (menuIconMatched) {
                drawSlotHighlight(context, slotBaseX, slotBaseY);
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal("Unlocked"), slotBaseX + SLOT_SIZE + 4, slotBaseY + 4, 0xFF55DD77);
            context.getMatrices().popMatrix();
            return;
        }

        List<ItemStack> stacks = entry.snapshot().stacks();
        for (int index = 0; index < stacks.size(); index++) {
            int slotX = slotBaseX + ((index % 9) * SLOT_SIZE);
            int slotY = slotBaseY + ((index / 9) * SLOT_SIZE);

            context.fill(slotX, slotY, slotX + SLOT_RENDER_SIZE, slotY + SLOT_RENDER_SIZE, 0xA0101010);

            ItemStack stack = stacks.get(index);
            boolean itemMatched = stackMatchesSearch(stack);
            if (itemMatched) {
                fillSlotHighlight(context, slotX, slotY);
            }
            if (!stack.isEmpty()) {
                context.drawItem(stack, slotX, slotY);
                context.drawStackOverlay(this.textRenderer, stack, slotX, slotY);
            }
            if (itemMatched) {
                drawSlotHighlight(context, slotX, slotY);
            }
        }

        context.getMatrices().popMatrix();
    }

    private void fillSlotHighlight(DrawContext context, int x, int y) {
        context.fill(x, y, x + SLOT_RENDER_SIZE, y + SLOT_RENDER_SIZE, 0x55FFD84D);
    }

    private void drawSlotHighlight(DrawContext context, int x, int y) {
        int right = x + SLOT_RENDER_SIZE;
        int bottom = y + SLOT_RENDER_SIZE;
        context.fill(x - 1, y - 1, right + 1, y, 0xFFFFD84D);
        context.fill(x - 1, bottom, right + 1, bottom + 1, 0xFFFFD84D);
        context.fill(x - 1, y, x, bottom, 0xFFFFD84D);
        context.fill(right, y, right + 1, bottom, 0xFFFFD84D);
    }

    private HoveredSlot findHoveredSlot(VaultStorageManager.VaultBrowserEntry entry, int x, int y, int mouseX, int mouseY) {
        int localMouseX = MathHelper.floor((mouseX - x) / getPanelScale());
        int localMouseY = MathHelper.floor((mouseY - y) / getPanelScale());
        int slotBaseX = PANEL_PADDING;
        int slotBaseY = PANEL_HEADER_HEIGHT + PANEL_PADDING;

        if (entry.locked()) {
            if (localMouseX >= slotBaseX
                    && localMouseX < slotBaseX + SLOT_RENDER_SIZE
                    && localMouseY >= slotBaseY
                    && localMouseY < slotBaseY + SLOT_RENDER_SIZE
                    && !entry.menuIcon().isEmpty()) {
                List<Text> tooltip = new ArrayList<>();
                tooltip.add(entry.menuIcon().getName());
                if (!entry.price().isBlank()) {
                    tooltip.add(Text.literal(entry.price()));
                }
                if (!entry.legendLevelRequirement().isBlank()) {
                    tooltip.add(Text.literal(entry.legendLevelRequirement()));
                }
                return new HoveredSlot(entry.menuIcon(), List.copyOf(tooltip));
            }
            return null;
        }

        if (entry.snapshot() == null) {
            if (localMouseX >= slotBaseX
                    && localMouseX < slotBaseX + SLOT_RENDER_SIZE
                    && localMouseY >= slotBaseY
                    && localMouseY < slotBaseY + SLOT_RENDER_SIZE
                    && !entry.menuIcon().isEmpty()) {
                return new HoveredSlot(entry.menuIcon(), List.of(entry.menuIcon().getName()));
            }
            return null;
        }

        for (int index = 0; index < entry.snapshot().stacks().size(); index++) {
            ItemStack stack = entry.snapshot().stacks().get(index);
            if (stack.isEmpty()) {
                continue;
            }

            int slotX = slotBaseX + ((index % 9) * SLOT_SIZE);
            int slotY = slotBaseY + ((index / 9) * SLOT_SIZE);

            if (localMouseX >= slotX && localMouseX < slotX + SLOT_RENDER_SIZE && localMouseY >= slotY && localMouseY < slotY + SLOT_RENDER_SIZE) {
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
        List<VaultStorageManager.VaultBrowserEntry> entries = VaultStorageManager.getBrowserEntries();
        if (entries.isEmpty()) {
            return null;
        }

        int columns = getColumnCount();
        int panelWidth = getPanelWidth();
        int totalWidth = columns * panelWidth + (columns - 1) * getPanelGapX();
        int startX = Math.max(getOuterMargin(), (this.width - totalWidth) / 2);
        int baseY = SCREEN_HEADER_HEIGHT - this.scroll;

        int x = startX;
        int y = baseY;
        int columnIndex = 0;
        int rowMaxHeight = 0;

        for (VaultStorageManager.VaultBrowserEntry entry : entries) {
            int panelHeight = getPanelHeight(entry);
            rowMaxHeight = Math.max(rowMaxHeight, panelHeight);

            if (mouseX >= x && mouseX < x + panelWidth && mouseY >= y && mouseY < y + panelHeight) {
                return new VaultPanel(entry);
            }

            columnIndex++;
            if (columnIndex >= columns) {
                columnIndex = 0;
                x = startX;
                y += rowMaxHeight + getPanelGapY();
                rowMaxHeight = 0;
            } else {
                x += panelWidth + getPanelGapX();
            }
        }

        return null;
    }

    private record HoveredSlot(ItemStack stack, List<Text> tooltip) {
    }

    public record VaultPanel(VaultStorageManager.VaultBrowserEntry entry) {
    }
}
