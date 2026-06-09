package legends.ultra.cool.addons.hud;

import legends.ultra.cool.addons.LegendsAddon;
import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.widget.settings.ColorPicker;
import legends.ultra.cool.addons.input.Keybinds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HudEditorScreen extends Screen {
    private static final String EDITOR_CONFIG_ID = "__hud_editor__";
    private static final String SNAP_ENABLED_KEY = "snapEnabled";

    private static final int SNAP_DISTANCE = 6;
    private static final int SNAP_GUIDE_COLOR = 0xCC4AA3DF;
    private static final int HOTBAR_GUIDE_COLOR = 0x664AA3DF;
    private static final int HOTBAR_WIDTH = 181;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_SLOT_SIZE = 20;
    private static final int HOTBAR_SLOT_OFFSET_X = 1;
    private static final int HOTBAR_SLOT_OFFSET_Y = 1;
    private static final int HOTBAR_SLOT_COUNT = 9;

    private static final int MODAL_W = 220;
    private static final int MODAL_MIN_H = 180;
    private static final int MODAL_PAD = 8;
    private static final int SETTINGS_ROW_H = 16;
    private static final int SETTINGS_ROW_GAP = 6;
    private static final int RESET_W = 12;
    private static final int RESET_H = 12;

    private static final int LAUNCHER_SQUARE = 40;
    private static final int LAUNCHER_WIDE = 100;
    private static final int LAUNCHER_GAP = 10;
    private static final int CHIP_H = 18;
    private static final int BACK_W = 44;
    private static final int SNAP_W = 70;
    private static final int MODS_VIEW_TOP = 42;
    private static final int MODS_VIEW_BOTTOM = 18;
    private static final int MODS_SCROLL_STEP = 28;

    private static final int MOD_TILE_SIZE = 96;
    private static final int MOD_TILE_MIN_SIZE = 72;
    private static final int MOD_TILE_GAP = 14;
    private static final int MOD_TILE_ICON_SIZE = 34;
    private static final int LAUNCHER_ICON_SIZE = 22;

    private static final int UI_PANEL_COLOR = 0xFF111111;
    private static final int UI_PANEL_HOVER_COLOR = 0xFF1A1A1A;
    private static final int UI_BORDER_COLOR = 0xFFFFFFFF;
    private static final int UI_BORDER_DARK = 0xFF000000;
    private static final int UI_BUTTON_TEXT_COLOR = 0xFFFFFFFF;
    private static final int UI_SUBTEXT_COLOR = 0xFFAAAAAA;
    private static final int MOD_TILE_ENABLED_COLOR = 0xFF26C665;
    private static final int MOD_TILE_ENABLED_FOOTER = 0xFF1D8E49;
    private static final int MOD_TILE_DISABLED_COLOR = 0xFF111111;
    private static final int MOD_TILE_DISABLED_FOOTER = 0xFF0A0A0A;
    private static final int MOD_TILE_DISABLED_BORDER = 0xFF000000;
    private static final int BUTTON_FILL_COLOR = 0xFF333333;
    private static final int BUTTON_FILL_HOVER_COLOR = 0xFF444444;
    private static final int BUTTON_BORDER_COLOR = 0xFFFFFFFF;
    private static final int BUTTON_BORDER_HOVER_COLOR = 0xFFFFFFFF;

    // Resource paths are relative to assets/legends-addon/. Leave blank until the texture exists.
    private static final String LAYOUT_BUTTON_ICON_PATH = "textures/icon/layout_icon.png";
    private static final String GENERAL_SETTINGS_BUTTON_ICON_PATH = "textures/icon/settings_icon.png";
    private static final String TEXT_WIDGET_ICON_PATH = "";
    private static final String COUNTER_WIDGET_ICON_PATH = "";
    private static final String TIMER_WIDGET_ICON_PATH = "textures/icon/stopwatch_icon.png";
    private static final String NAMEPLATES_WIDGET_ICON_PATH = "textures/icon/nameplate_icon.png";
    private static final String BETTER_DIALOGUE_WIDGET_ICON_PATH = "textures/icon/better_dialogue_icon.png";
    private static final String UI_TOGGLE_WIDGET_ICON_PATH = "textures/icon/ui_toggle_icon.png";
    private static final String HEALTH_DISPLAY_WIDGET_ICON_PATH = "textures/icon/health_display_icon.png";
    private static final String MANA_DISPLAY_WIDGET_ICON_PATH = "textures/icon/mana_display_icon.png";
    private static final String DEFENSE_DISPLAY_WIDGET_ICON_PATH = "textures/icon/defense_display_icon.png";
    private static final String COOLDOWN_DISPLAY_WIDGET_ICON_PATH = "textures/icon/cooldown_display_icon.png";
    private static final String VAULT_BROWSER_WIDGET_ICON_PATH = "textures/icon/vault_browser_icon.png";

    private final List<KeybindRow> keybindRows = List.of(
            new KeybindRow("Open editor", () -> Keybinds.OPEN_EDITOR),
            new KeybindRow("Toggle timer", () -> Keybinds.TOGGLE_TIMER),
            new KeybindRow("Reset timer", () -> Keybinds.RESET_TIMER)
    );

    private HudWidget dragging;
    private BarDraggable draggingBar;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean snapEnabled = true;
    private double activeSnapGuideX = Double.NaN;
    private double activeSnapGuideY = Double.NaN;
    private int modsScroll = 0;
    private int modsMaxScroll = 0;

    private ViewMode viewMode = ViewMode.HOME;
    private HudWidget settingsWidget;
    private ColorPicker colorPicker;
    private String openColorKey;
    private String draggingSliderKey;
    private KeybindRow editingKeybindRow;
    private int settingsRowIndex = 0;

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
        snapEnabled = WidgetConfigManager.getBool(EDITOR_CONFIG_ID, SNAP_ENABLED_KEY, true);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (editingKeybindRow != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                editingKeybindRow = null;
                return true;
            }

            if (input.key() == GLFW.GLFW_KEY_BACKSPACE || input.key() == GLFW.GLFW_KEY_DELETE) {
                applyCapturedKeybind(editingKeybindRow, InputUtil.UNKNOWN_KEY);
                return true;
            }

            applyCapturedKeybind(editingKeybindRow, InputUtil.fromKeyCode(input));
            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (isSettingsOpen()) {
                closeSettingsModal();
                return true;
            }

            if (viewMode != ViewMode.HOME) {
                setViewMode(ViewMode.HOME);
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (isSettingsOpen()) {
            if (colorPicker != null && colorPicker.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return handleSettingsClick(mouseX, mouseY, button);
        }

        return switch (viewMode) {
            case HOME -> handleHomeClick(mouseX, mouseY);
            case MODS -> handleModsClick(mouseX, mouseY);
            case LAYOUT -> handleLayoutClick(mouseX, mouseY);
            case GENERAL -> handleGeneralClick(mouseX, mouseY);
        };
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (isSettingsOpen()) {
            if (colorPicker != null && colorPicker.mouseDragged(mouseX, mouseY, button, dx, dy)) {
                return true;
            }

            if (draggingSliderKey != null) {
                SettingsLayout layout = beginSettingsLayout();
                for (HudWidget.HudSetting setting : safeSettings(settingsWidget)) {
                    int rowY = nextRowY(layout);
                    if (setting.type() != HudWidget.HudSetting.Type.SLIDER) {
                        continue;
                    }
                    if (!setting.key().equals(draggingSliderKey)) {
                        continue;
                    }
                    if (!setting.enabled().getAsBoolean()) {
                        continue;
                    }

                    float nextValue = sliderValueForMouse(setting, layout.sliderBarX, layout.sliderBarW, mouseX);
                    setting.setFloat().accept(nextValue);
                    WidgetConfigManager.updateWidget(settingsWidget);
                    return true;
                }
            }

            return true;
        }

        if (viewMode != ViewMode.LAYOUT) {
            return true;
        }

        if (draggingBar != null) {
            moveDraggingBarToMouse(mouseX, mouseY);
            return true;
        }

        if (dragging != null) {
            moveDraggingWidgetToMouse(mouseX, mouseY);
            return true;
        }

        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (isSettingsOpen()) {
            draggingSliderKey = null;
            if (colorPicker != null && colorPicker.mouseReleased(mouseX, mouseY, button)) {
                WidgetConfigManager.updateWidget(settingsWidget);
            }
            return true;
        }

        if (viewMode != ViewMode.LAYOUT) {
            clearDragState();
            return true;
        }

        if (draggingBar != null) {
            draggingBar.saveBarPosition();
            draggingBar = null;
            clearSnapGuides();
            return true;
        }

        if (dragging != null) {
            WidgetConfigManager.updateWidget(dragging);
            dragging = null;
            clearSnapGuides();
            return true;
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (viewMode == ViewMode.MODS) {
            updateModsScrollBounds();
            if (modsMaxScroll > 0 && modsViewportBounds().contains(mouseX, mouseY)) {
                modsScroll = MathHelper.clamp(modsScroll - (int) Math.round(verticalAmount * MODS_SCROLL_STEP), 0, modsMaxScroll);
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackdrop(ctx);

        int uiMouseX = isSettingsOpen() ? Integer.MIN_VALUE : mouseX;
        int uiMouseY = isSettingsOpen() ? Integer.MIN_VALUE : mouseY;

        if (viewMode != ViewMode.MODS) {
            for (HudWidget widget : HudManager.getWidgets()) {
                if (widget.isEnabled()) {
                    widget.render(ctx);
                }
            }
        }

        renderSnapGuides(ctx);

        switch (viewMode) {
            case HOME -> renderHome(ctx, uiMouseX, uiMouseY);
            case MODS -> renderMods(ctx, uiMouseX, uiMouseY);
            case LAYOUT -> renderLayoutChrome(ctx, uiMouseX, uiMouseY);
            case GENERAL -> renderGeneral(ctx, uiMouseX, uiMouseY);
        }

        renderSettingsModal(ctx, mouseX, mouseY);
    }

    private boolean handleHomeClick(double mouseX, double mouseY) {
        LauncherLayout launcher = launcherLayout();
        if (launcher.layoutButton.contains(mouseX, mouseY)) {
            setViewMode(ViewMode.LAYOUT);
            return true;
        }
        if (launcher.modsButton.contains(mouseX, mouseY)) {
            setViewMode(ViewMode.MODS);
            return true;
        }
        if (launcher.settingsButton.contains(mouseX, mouseY)) {
            setViewMode(ViewMode.GENERAL);
            return true;
        }
        return true;
    }

    private boolean handleModsClick(double mouseX, double mouseY) {
        if (backChipBounds().contains(mouseX, mouseY)) {
            setViewMode(ViewMode.HOME);
            return true;
        }

        Rect viewport = modsViewportBounds();
        if (!viewport.contains(mouseX, mouseY)) {
            return true;
        }

        for (WidgetTile tile : widgetTiles()) {
            if (tile.widget.hasSettings() && tile.settingsButton.contains(mouseX, mouseY)) {
                openWidgetSettings(tile.widget);
                return true;
            }

            if (tile.bounds.contains(mouseX, mouseY)) {
                tile.widget.toggle();
                WidgetConfigManager.updateWidget(tile.widget);
                return true;
            }
        }

        return true;
    }

    private boolean handleLayoutClick(double mouseX, double mouseY) {
        if (backChipBounds().contains(mouseX, mouseY)) {
            setViewMode(ViewMode.HOME);
            return true;
        }

        if (snapChipBounds().contains(mouseX, mouseY)) {
            snapEnabled = !snapEnabled;
            WidgetConfigManager.setBool(EDITOR_CONFIG_ID, SNAP_ENABLED_KEY, snapEnabled, true);
            return true;
        }

        BarDraggable bar = getBarUnderMouse(mouseX, mouseY);
        if (bar != null) {
            beginBarDrag(bar, mouseX, mouseY);
            return true;
        }

        for (HudWidget widget : HudManager.getWidgets()) {
            if (!widget.isEnabled()) {
                continue;
            }
            if (widget.isMouseOver(mouseX, mouseY)) {
                beginWidgetDrag(widget, mouseX, mouseY);
                return true;
            }
        }

        return true;
    }

    private boolean handleGeneralClick(double mouseX, double mouseY) {
        if (backChipBounds().contains(mouseX, mouseY)) {
            editingKeybindRow = null;
            setViewMode(ViewMode.HOME);
            return true;
        }

        for (int index = 0; index < keybindRows.size(); index++) {
            KeybindRow row = keybindRows.get(index);
            if (keybindValueBounds(keybindRowBounds(index)).contains(mouseX, mouseY)) {
                editingKeybindRow = row;
                return true;
            }
        }

        if (editingKeybindRow != null) {
            editingKeybindRow = null;
            return true;
        }

        return true;
    }

    private void renderBackdrop(DrawContext ctx) {
        int top = viewMode == ViewMode.LAYOUT ? 0x44000000 : 0x88000000;
        int bottom = viewMode == ViewMode.LAYOUT ? 0x66000000 : 0xAA000000;
        ctx.fillGradient(0, 0, this.width, this.height, top, bottom);
    }

    private void renderHome(DrawContext ctx, int mouseX, int mouseY) {
        LauncherLayout launcher = launcherLayout();

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("HUD Editor"), this.width / 2, launcher.modsButton.y - 24, UI_BUTTON_TEXT_COLOR);

        drawLauncherCard(ctx, launcher.layoutButton, mouseX, mouseY, "", LAYOUT_BUTTON_ICON_PATH, false);
        drawLauncherCard(ctx, launcher.modsButton, mouseX, mouseY, "Mods", null, true);
        drawLauncherCard(ctx, launcher.settingsButton, mouseX, mouseY, "", GENERAL_SETTINGS_BUTTON_ICON_PATH, false);
    }

    private void renderMods(DrawContext ctx, int mouseX, int mouseY) {
        drawChip(ctx, backChipBounds(), mouseX, mouseY, "Back", BUTTON_FILL_COLOR, UI_BUTTON_TEXT_COLOR);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Mods"), this.width / 2, 18, UI_BUTTON_TEXT_COLOR);

        Rect viewport = modsViewportBounds();
        updateModsScrollBounds();
        int viewportMouseX = viewport.contains(mouseX, mouseY) ? mouseX : Integer.MIN_VALUE;
        int viewportMouseY = viewport.contains(mouseX, mouseY) ? mouseY : Integer.MIN_VALUE;

        ctx.enableScissor(viewport.x, viewport.y, viewport.right(), viewport.bottom());
        for (WidgetTile tile : widgetTiles()) {
            renderWidgetTile(ctx, tile, viewportMouseX, viewportMouseY);
        }
        ctx.disableScissor();

        renderModsScrollBar(ctx, viewport);
    }

    private void renderLayoutChrome(DrawContext ctx, int mouseX, int mouseY) {
        drawChip(ctx, backChipBounds(), mouseX, mouseY, "Back", 0x80333333, UI_BUTTON_TEXT_COLOR);
        drawChip(ctx, snapChipBounds(), mouseX, mouseY, snapEnabled ? "Snap ON" : "Snap OFF", snapEnabled ? 0xFF26C665 : 0x80333333, UI_BUTTON_TEXT_COLOR);

        String hint = "Drag enabled widgets. Press ESC to return.";
        int hintX = this.width - textRenderer.getWidth(hint) - 14;
        ctx.drawText(textRenderer, hint, Math.max(14, hintX), 17, UI_SUBTEXT_COLOR, false);
    }

    private void renderGeneral(DrawContext ctx, int mouseX, int mouseY) {
        Rect panel = generalPanelBounds();

        drawPanel(ctx, panel, UI_PANEL_COLOR, UI_PANEL_COLOR, UI_BORDER_COLOR);
        drawChip(ctx, backChipBounds(), mouseX, mouseY, "Back", BUTTON_FILL_COLOR, UI_BUTTON_TEXT_COLOR);

        ctx.drawText(textRenderer, "General Settings", panel.x + 18, panel.y + 14, UI_BUTTON_TEXT_COLOR, false);
        ctx.drawText(textRenderer, "Use Minecraft controls to rebind. Widget-specific options stay under Mods.", panel.x + 18, panel.y + 28, UI_SUBTEXT_COLOR, false);

        for (int index = 0; index < keybindRows.size(); index++) {
            Rect rowBounds = keybindRowBounds(index);
            renderKeybindRow(ctx, rowBounds, keybindRows.get(index), mouseX, mouseY);
        }

        String hint = editingKeybindRow == null
                ? "Click a binding to change it. Backspace/Delete clears. Esc cancels."
                : "Press a key now. Backspace/Delete clears. Esc cancels.";
        ctx.drawText(textRenderer, hint, panel.x + 14, panel.bottom() - 16, UI_SUBTEXT_COLOR, false);
    }

    private void drawLauncherCard(DrawContext ctx, Rect rect, int mouseX, int mouseY,
                                  String label, String iconPath, boolean centerLabelOnly) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int top = hovered ? UI_PANEL_HOVER_COLOR : UI_PANEL_COLOR;
        int bottom = top;
        int border = hovered ? BUTTON_BORDER_HOVER_COLOR : BUTTON_BORDER_COLOR;

        drawPanel(ctx, rect, top, bottom, border);

        if (centerLabelOnly) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), rect.x + rect.width / 2, rect.y + (rect.height - textRenderer.fontHeight) / 2, UI_BUTTON_TEXT_COLOR);
            return;
        }

        int labelHeight = label.isBlank() ? 0 : textRenderer.fontHeight;
        int iconAreaHeight = Math.max(LAUNCHER_ICON_SIZE, rect.height - labelHeight);
        int iconX = rect.x + (rect.width - LAUNCHER_ICON_SIZE) / 2;
        int iconY = rect.y + Math.max(0, (iconAreaHeight - LAUNCHER_ICON_SIZE) / 2);
        int labelY = rect.bottom() - 16;

        if (drawIconIfPresent(ctx, iconPath, iconX, iconY, LAUNCHER_ICON_SIZE)) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), rect.x + rect.width / 2, labelY, UI_BUTTON_TEXT_COLOR);
            return;
        }

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), rect.x + rect.width / 2, rect.y + (rect.height - textRenderer.fontHeight) / 2, UI_BUTTON_TEXT_COLOR);
    }

    private void renderWidgetTile(DrawContext ctx, WidgetTile tile, int mouseX, int mouseY) {
        boolean hovered = tile.bounds.contains(mouseX, mouseY);
        boolean hasSettings = tile.widget.hasSettings();
        boolean settingsHovered = hasSettings && tile.settingsButton.contains(mouseX, mouseY);
        boolean enabled = tile.widget.isEnabled();
        int footerHeight = tile.settingsButton.height;
        int footerTop = tile.bounds.bottom() - footerHeight;
        int footerRight = hasSettings ? tile.settingsButton.x : tile.bounds.right();

        int bodyColor = enabled ? MOD_TILE_ENABLED_COLOR : MOD_TILE_DISABLED_COLOR;
        int footerColor = enabled ? MOD_TILE_ENABLED_FOOTER : MOD_TILE_DISABLED_FOOTER;
        int bodyBorder = hovered ? UI_BORDER_COLOR : MOD_TILE_DISABLED_BORDER;
        int settingsColor = settingsHovered ? BUTTON_FILL_HOVER_COLOR : BUTTON_FILL_COLOR;
        int bodyHeight = footerTop - tile.bounds.y;

        ctx.fill(tile.bounds.x, tile.bounds.y, tile.bounds.right(), footerTop, bodyColor);
        ctx.fill(tile.bounds.x, footerTop, footerRight, tile.bounds.bottom(), footerColor);
        if (hasSettings) {
            ctx.fill(tile.settingsButton.x, footerTop, tile.settingsButton.right(), tile.settingsButton.bottom(), settingsColor);
        }
        drawBorder(ctx, tile.bounds.x, tile.bounds.y, tile.bounds.width, tile.bounds.height, bodyBorder);
        ctx.drawHorizontalLine(tile.bounds.x, tile.bounds.right(), footerTop, 0x66000000);
        if (hasSettings) {
            ctx.drawVerticalLine(tile.settingsButton.x, footerTop, tile.settingsButton.bottom(), 0x66000000);
        }

        drawIconIfPresent(
                ctx,
                iconPathForWidget(tile.widget),
                tile.bounds.x + (tile.bounds.width - MOD_TILE_ICON_SIZE) / 2,
                tile.bounds.y + Math.max(0, (bodyHeight - MOD_TILE_ICON_SIZE) / 2),
                MOD_TILE_ICON_SIZE
        );

        renderScrollingTileLabel(
                ctx,
                tile.widget.getName(),
                tile.bounds.x + 6,
                footerTop + 5,
                footerRight - tile.bounds.x - 12,
                footerHeight - 2
        );

        if (hasSettings) {
            drawMiniSettingsIcon(ctx, tile.settingsButton);
        }
    }

    private void renderKeybindRow(DrawContext ctx, Rect rowBounds, KeybindRow row, int mouseX, int mouseY) {
        Rect valueBounds = keybindValueBounds(rowBounds);
        boolean editing = row == editingKeybindRow;
        boolean valueHovered = valueBounds.contains(mouseX, mouseY);

        ctx.fill(rowBounds.x, rowBounds.y, rowBounds.right(), rowBounds.bottom(), BUTTON_FILL_COLOR);
        drawBorder(ctx, rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height, UI_BORDER_DARK);

        ctx.drawText(textRenderer, row.label, rowBounds.x + 8, rowBounds.y + 5, UI_BUTTON_TEXT_COLOR, false);

        int valueFill = editing ? UI_PANEL_HOVER_COLOR : (valueHovered ? BUTTON_FILL_HOVER_COLOR : UI_PANEL_COLOR);
        int valueBorder = editing ? UI_BORDER_COLOR : UI_BORDER_DARK;
        ctx.fill(valueBounds.x, valueBounds.y, valueBounds.right(), valueBounds.bottom(), valueFill);
        drawBorder(ctx, valueBounds.x, valueBounds.y, valueBounds.width, valueBounds.height, valueBorder);

        String value = editing ? "Press key..." : boundKeyName(row.bindingSupplier.get());
        ctx.drawText(textRenderer, value, valueBounds.x + 6, rowBounds.y + 5, editing ? UI_BUTTON_TEXT_COLOR : UI_SUBTEXT_COLOR, false);
    }

    private void renderModsScrollBar(DrawContext ctx, Rect viewport) {
        if (modsMaxScroll <= 0) {
            return;
        }

        int trackWidth = 4;
        int trackX = this.width - 10;
        int trackY = viewport.y;
        int trackHeight = viewport.height;
        int thumbHeight = Math.max(18, (int) Math.round(trackHeight * (viewport.height / (double) (viewport.height + modsMaxScroll))));
        int thumbTravel = trackHeight - thumbHeight;
        int thumbY = trackY + (modsMaxScroll == 0 ? 0 : (int) Math.round((modsScroll / (double) modsMaxScroll) * thumbTravel));

        ctx.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight, 0x66000000);
        ctx.fill(trackX, thumbY, trackX + trackWidth, thumbY + thumbHeight, 0xCCFFFFFF);
    }

    private void drawMiniSettingsIcon(DrawContext ctx, Rect rect) {
        int iconSize = Math.max(8, Math.min(12, Math.min(rect.width, rect.height) - 6));
        int iconX = rect.x + (rect.width - iconSize) / 2;
        int iconY = rect.y + (rect.height - iconSize) / 2;

        if (!drawIconIfPresent(ctx, GENERAL_SETTINGS_BUTTON_ICON_PATH, iconX, iconY, iconSize)) {
            int cx = rect.x + rect.width / 2;
            int cy = rect.y + rect.height / 2;
            ctx.fill(rect.x + 4, cy - 1, rect.right() - 4, cy + 1, UI_BUTTON_TEXT_COLOR);
            ctx.fill(cx - 1, rect.y + 4, cx + 1, rect.bottom() - 4, UI_BUTTON_TEXT_COLOR);
        }
    }

    private void drawPanel(DrawContext ctx, Rect rect, int topColor, int bottomColor, int borderColor) {
        ctx.fill(rect.x, rect.y, rect.right(), rect.bottom(), topColor);
        drawBorder(ctx, rect.x, rect.y, rect.width, rect.height, borderColor);
    }

    private void drawChip(DrawContext ctx, Rect rect, int mouseX, int mouseY, String label, int fillColor, int textColor) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int color = hovered ? BUTTON_FILL_HOVER_COLOR : fillColor;
        ctx.fill(rect.x, rect.y, rect.right(), rect.bottom(), color);
        drawBorder(ctx, rect.x, rect.y, rect.width, rect.height, 0x00000000);
        ctx.drawText(textRenderer, label, rect.x + 8, rect.y + 5, textColor, false);
    }

    private LauncherLayout launcherLayout() {
        int totalWidth = LAUNCHER_SQUARE + LAUNCHER_GAP + LAUNCHER_WIDE + LAUNCHER_GAP + LAUNCHER_SQUARE;
        int startX = (this.width - totalWidth) / 2;
        int y = (this.height - LAUNCHER_SQUARE) / 2;

        Rect layout = new Rect(startX, y, LAUNCHER_SQUARE, LAUNCHER_SQUARE);
        Rect mods = new Rect(layout.right() + LAUNCHER_GAP, y, LAUNCHER_WIDE, LAUNCHER_SQUARE);
        Rect settings = new Rect(mods.right() + LAUNCHER_GAP, y, LAUNCHER_SQUARE, LAUNCHER_SQUARE);
        return new LauncherLayout(layout, mods, settings);
    }

    private Rect modsViewportBounds() {
        return new Rect(0, MODS_VIEW_TOP, this.width, Math.max(0, this.height - MODS_VIEW_TOP - MODS_VIEW_BOTTOM));
    }

    private Rect generalPanelBounds() {
        int panelWidth = 404;
        int panelHeight = 66 + keybindRows.size() * 24;
        return new Rect((this.width - panelWidth) / 2, (this.height - panelHeight) / 2, panelWidth, panelHeight);
    }

    private Rect keybindRowBounds(int index) {
        Rect panel = generalPanelBounds();
        int rowX = panel.x + 14;
        int rowY = panel.y + 48 + index * 24;
        return new Rect(rowX, rowY, panel.width - 28, 18);
    }

    private Rect keybindValueBounds(Rect rowBounds) {
        int valueWidth = 126;
        int valueHeight = rowBounds.height - 4;
        int valueX = rowBounds.right() - valueWidth - 4;
        int valueY = rowBounds.y + 2;
        return new Rect(valueX, valueY, valueWidth, valueHeight);
    }

    private void renderScrollingTileLabel(DrawContext ctx, String label, int x, int y, int width, int height) {
        int textWidth = textRenderer.getWidth(label);
        if (textWidth <= width) {
            ctx.drawText(textRenderer, label, x, y, UI_BUTTON_TEXT_COLOR, false);
            return;
        }

        int overflow = textWidth - width;
        double cycle = (System.currentTimeMillis() % 4000L) / 4000.0d;
        double pingPong = cycle < 0.5d ? cycle * 2d : (1d - cycle) * 2d;
        int textX = x - (int) Math.round(overflow * pingPong);

        ctx.enableScissor(x, y - 1, x + width, y + height);
        ctx.drawText(textRenderer, label, textX, y, UI_BUTTON_TEXT_COLOR, false);
        ctx.disableScissor();
    }

    private List<WidgetTile> widgetTiles() {
        List<HudWidget> widgets = HudManager.getWidgets();
        GridMetrics grid = computeGridMetrics(widgets.size());
        List<WidgetTile> tiles = new ArrayList<>(widgets.size());

        int footerHeight = Math.max(18, grid.tileSize / 5);
        for (int index = 0; index < widgets.size(); index++) {
            HudWidget widget = widgets.get(index);
            int col = index % grid.columns;
            int row = index / grid.columns;
            int x = grid.startX + col * (grid.tileSize + grid.gap);
            int y = grid.startY + row * (grid.tileSize + grid.gap);
            Rect bounds = new Rect(x, y, grid.tileSize, grid.tileSize);
            Rect settingsButton = new Rect(bounds.right() - footerHeight, bounds.bottom() - footerHeight, footerHeight, footerHeight);
            tiles.add(new WidgetTile(widget, bounds, settingsButton));
        }

        return tiles;
    }

    private GridMetrics computeGridMetrics(int count) {
        int safeCount = Math.max(1, count);
        int availableWidth = Math.max(220, this.width - 120);
        int maxColumns = Math.min(4, safeCount);
        Rect modsViewport = modsViewportBounds();

        GridMetrics best = null;
        for (int columns = maxColumns; columns >= 1; columns--) {
            int tileSize = Math.min(MOD_TILE_SIZE, (availableWidth - MOD_TILE_GAP * (columns - 1)) / columns);
            if (tileSize < MOD_TILE_MIN_SIZE) {
                continue;
            }

            int rows = (safeCount + columns - 1) / columns;
            int gridWidth = columns * tileSize + (columns - 1) * MOD_TILE_GAP;
            int gridHeight = rows * tileSize + (rows - 1) * MOD_TILE_GAP;
            int startX = (this.width - gridWidth) / 2;
            int startY = gridHeight <= modsViewport.height
                    ? modsViewport.y + Math.max(0, (modsViewport.height - gridHeight) / 2)
                    : modsViewport.y - modsScroll;
            best = new GridMetrics(tileSize, MOD_TILE_GAP, columns, rows, startX, startY, gridWidth, gridHeight);
            break;
        }

        if (best != null) {
            return best;
        }

        int columns = Math.min(2, safeCount);
        int rows = (safeCount + columns - 1) / columns;
        int tileSize = MOD_TILE_MIN_SIZE;
        int gridWidth = columns * tileSize + (columns - 1) * MOD_TILE_GAP;
        int gridHeight = rows * tileSize + (rows - 1) * MOD_TILE_GAP;
        int startX = (this.width - gridWidth) / 2;
        int startY = gridHeight <= modsViewport.height
                ? modsViewport.y + Math.max(0, (modsViewport.height - gridHeight) / 2)
                : modsViewport.y - modsScroll;
        return new GridMetrics(tileSize, MOD_TILE_GAP, columns, rows, startX, startY, gridWidth, gridHeight);
    }

    private Rect backChipBounds() {
        return new Rect(14, 14, BACK_W, CHIP_H);
    }

    private Rect snapChipBounds() {
        return new Rect(backChipBounds().right() + 8, 14, SNAP_W, CHIP_H);
    }

    private void openWidgetSettings(HudWidget widget) {
        settingsWidget = widget;
        colorPicker = null;
        openColorKey = null;
        draggingSliderKey = null;
    }

    private boolean isSettingsOpen() {
        return settingsWidget != null;
    }

    private void closeSettingsModal() {
        if (settingsWidget != null) {
            WidgetConfigManager.updateWidget(settingsWidget);
        }
        settingsWidget = null;
        colorPicker = null;
        openColorKey = null;
        draggingSliderKey = null;
    }

    private void setViewMode(ViewMode nextMode) {
        viewMode = nextMode;
        if (viewMode != ViewMode.LAYOUT) {
            clearDragState();
        }
        if (viewMode != ViewMode.GENERAL) {
            editingKeybindRow = null;
        }
        if (viewMode == ViewMode.MODS) {
            updateModsScrollBounds();
        }
    }

    private void updateModsScrollBounds() {
        Rect viewport = modsViewportBounds();
        GridMetrics grid = computeUnscrolledGridMetrics(HudManager.getWidgets().size(), viewport);
        modsMaxScroll = Math.max(0, grid.gridHeight - viewport.height);
        modsScroll = MathHelper.clamp(modsScroll, 0, modsMaxScroll);
    }

    private GridMetrics computeUnscrolledGridMetrics(int count, Rect modsViewport) {
        int safeCount = Math.max(1, count);
        int availableWidth = Math.max(220, this.width - 120);
        int maxColumns = Math.min(4, safeCount);

        for (int columns = maxColumns; columns >= 1; columns--) {
            int tileSize = Math.min(MOD_TILE_SIZE, (availableWidth - MOD_TILE_GAP * (columns - 1)) / columns);
            if (tileSize < MOD_TILE_MIN_SIZE) {
                continue;
            }

            int rows = (safeCount + columns - 1) / columns;
            int gridWidth = columns * tileSize + (columns - 1) * MOD_TILE_GAP;
            int gridHeight = rows * tileSize + (rows - 1) * MOD_TILE_GAP;
            int startX = (this.width - gridWidth) / 2;
            int startY = gridHeight <= modsViewport.height
                    ? modsViewport.y + Math.max(0, (modsViewport.height - gridHeight) / 2)
                    : modsViewport.y;
            return new GridMetrics(tileSize, MOD_TILE_GAP, columns, rows, startX, startY, gridWidth, gridHeight);
        }

        int columns = Math.min(2, safeCount);
        int rows = (safeCount + columns - 1) / columns;
        int tileSize = MOD_TILE_MIN_SIZE;
        int gridWidth = columns * tileSize + (columns - 1) * MOD_TILE_GAP;
        int gridHeight = rows * tileSize + (rows - 1) * MOD_TILE_GAP;
        int startX = (this.width - gridWidth) / 2;
        int startY = gridHeight <= modsViewport.height
                ? modsViewport.y + Math.max(0, (modsViewport.height - gridHeight) / 2)
                : modsViewport.y;
        return new GridMetrics(tileSize, MOD_TILE_GAP, columns, rows, startX, startY, gridWidth, gridHeight);
    }

    private void clearDragState() {
        dragging = null;
        draggingBar = null;
        clearSnapGuides();
    }

    private void beginWidgetDrag(HudWidget widget, double mouseX, double mouseY) {
        dragging = widget;
        dragOffsetX = mouseX - widget.x;
        dragOffsetY = mouseY - widget.y;
    }

    private void beginBarDrag(BarDraggable bar, double mouseX, double mouseY) {
        draggingBar = bar;
        dragOffsetX = mouseX - bar.getBarX();
        dragOffsetY = mouseY - bar.getBarY();
    }

    private void moveDraggingWidgetToMouse(double mouseX, double mouseY) {
        double targetX = mouseX - dragOffsetX;
        double targetY = mouseY - dragOffsetY;

        if (snapEnabled) {
            SnapPosition snapped = snapPosition(
                    targetX,
                    targetY,
                    0d,
                    0d,
                    dragging.getWidth(),
                    dragging.getHeight(),
                    dragging,
                    null
            );
            targetX = snapped.x;
            targetY = snapped.y;
        } else {
            clearSnapGuides();
        }

        dragging.x = clamp(targetX, 0, this.width - dragging.getWidth());
        dragging.y = clamp(targetY, 0, this.height - dragging.getHeight());
    }

    private void moveDraggingBarToMouse(double mouseX, double mouseY) {
        double targetX = mouseX - dragOffsetX;
        double targetY = mouseY - dragOffsetY;

        if (snapEnabled) {
            SnapPosition snapped = snapPosition(
                    targetX,
                    targetY,
                    draggingBar.getBarLeft() - draggingBar.getBarX(),
                    draggingBar.getBarTop() - draggingBar.getBarY(),
                    draggingBar.getBarWidth(),
                    draggingBar.getBarHeight(),
                    null,
                    draggingBar
            );
            targetX = snapped.x;
            targetY = snapped.y;
        } else {
            clearSnapGuides();
        }

        draggingBar.setBarPosition(targetX, targetY);
        draggingBar.clampBar(this.width, this.height);
    }

    private BarDraggable getBarUnderMouse(double mouseX, double mouseY) {
        for (HudWidget widget : HudManager.getWidgets()) {
            if (!widget.isEnabled()) {
                continue;
            }
            if (widget instanceof BarDraggable bar && bar.isMouseOverBar(mouseX, mouseY)) {
                return bar;
            }
        }
        return null;
    }

    private SnapPosition snapPosition(double targetX, double targetY, double objectOffsetX, double objectOffsetY,
                                      double objectWidth, double objectHeight,
                                      HudWidget excludedWidget, BarDraggable excludedBar) {
        clearSnapGuides();

        double snappedX = targetX;
        double snappedY = targetY;
        double bestX = SNAP_DISTANCE + 0.001d;
        double bestY = SNAP_DISTANCE + 0.001d;

        double[] sourceXOffsets = {objectOffsetX, objectOffsetX + objectWidth / 2d, objectOffsetX + objectWidth};
        double[] sourceYOffsets = {objectOffsetY, objectOffsetY + objectHeight / 2d, objectOffsetY + objectHeight};

        for (SnapRect target : collectSnapTargets(excludedWidget, excludedBar)) {
            double[] targetXAnchors = {target.left(), target.centerX(), target.right()};
            for (double targetAnchor : targetXAnchors) {
                for (double sourceOffset : sourceXOffsets) {
                    double candidateX = targetAnchor - sourceOffset;
                    double distance = Math.abs(candidateX - targetX);
                    if (distance < bestX) {
                        bestX = distance;
                        snappedX = candidateX;
                        activeSnapGuideX = targetAnchor;
                    }
                }
            }

            double[] targetYAnchors = {target.top(), target.centerY(), target.bottom()};
            for (double targetAnchor : targetYAnchors) {
                for (double sourceOffset : sourceYOffsets) {
                    double candidateY = targetAnchor - sourceOffset;
                    double distance = Math.abs(candidateY - targetY);
                    if (distance < bestY) {
                        bestY = distance;
                        snappedY = candidateY;
                        activeSnapGuideY = targetAnchor;
                    }
                }
            }
        }

        return new SnapPosition(snappedX, snappedY);
    }

    private List<SnapRect> collectSnapTargets(HudWidget excludedWidget, BarDraggable excludedBar) {
        List<SnapRect> targets = new ArrayList<>();
        targets.add(new SnapRect(0, 0, this.width, this.height));

        SnapRect hotbar = hotbarRect();
        targets.add(hotbar);
        for (int i = 0; i < HOTBAR_SLOT_COUNT; i++) {
            targets.add(new SnapRect(
                    hotbar.x + HOTBAR_SLOT_OFFSET_X + i * HOTBAR_SLOT_SIZE,
                    hotbar.y + HOTBAR_SLOT_OFFSET_Y,
                    HOTBAR_SLOT_SIZE,
                    HOTBAR_SLOT_SIZE
            ));
        }

        for (HudWidget widget : HudManager.getWidgets()) {
            if (widget == null || !widget.isEnabled()) {
                continue;
            }

            if (widget != excludedWidget) {
                double widgetWidth = widget.getWidth();
                double widgetHeight = widget.getHeight();
                if (widgetWidth > 0 && widgetHeight > 0) {
                    targets.add(new SnapRect(widget.x, widget.y, widgetWidth, widgetHeight));
                }
            }

            if (widget instanceof BarDraggable bar && bar != excludedBar && bar.isBarVisible()) {
                targets.add(new SnapRect(bar.getBarLeft(), bar.getBarTop(), bar.getBarWidth(), bar.getBarHeight()));
            }
        }

        return targets;
    }

    private SnapRect hotbarRect() {
        return new SnapRect((this.width - HOTBAR_WIDTH - 1) / 2d, this.height - HOTBAR_HEIGHT, HOTBAR_WIDTH, HOTBAR_HEIGHT);
    }

    private void clearSnapGuides() {
        activeSnapGuideX = Double.NaN;
        activeSnapGuideY = Double.NaN;
    }

    private void renderSnapGuides(DrawContext ctx) {
        if (viewMode != ViewMode.LAYOUT || !snapEnabled || isSettingsOpen()) {
            return;
        }

        SnapRect hotbar = hotbarRect();
        drawBorder(ctx, (int) Math.round(hotbar.x), (int) Math.round(hotbar.y), HOTBAR_WIDTH, HOTBAR_HEIGHT, HOTBAR_GUIDE_COLOR);

        if (!Double.isNaN(activeSnapGuideX)) {
            int x = (int) Math.round(activeSnapGuideX);
            ctx.drawVerticalLine(x, 0, this.height, SNAP_GUIDE_COLOR);
        }

        if (!Double.isNaN(activeSnapGuideY)) {
            int y = (int) Math.round(activeSnapGuideY);
            ctx.drawHorizontalLine(0, this.width, y, SNAP_GUIDE_COLOR);
        }
    }

    private void openColorPicker(HudWidget.HudSetting setting, int px, int py, int pw) {
        if (openColorKey != null && openColorKey.equals(setting.key())) {
            openColorKey = null;
            colorPicker = null;
            return;
        }

        openColorKey = setting.key();
        colorPicker = new ColorPicker(
                px, py, pw,
                () -> setting.getColor().getAsInt(),
                color -> {
                    setting.setColor().accept(color);
                    WidgetConfigManager.updateWidget(settingsWidget);
                }
        );
    }

    private boolean handleSettingsClick(double mouseX, double mouseY, int button) {
        int x = modalX();
        int y = modalY();

        int closeX = x + MODAL_W - 18;
        int closeY = y + 6;
        if (inside(mouseX, mouseY, closeX, closeY, 10, 10)) {
            closeSettingsModal();
            return true;
        }

        SettingsLayout layout = beginSettingsLayout();
        for (HudWidget.HudSetting setting : safeSettings(settingsWidget)) {
            int rowY = nextRowY(layout);

            if (setting.type() == HudWidget.HudSetting.Type.SECTION) {
                continue;
            }

            if (inside(mouseX, mouseY, layout.resetX, rowY - 1, RESET_W, RESET_H)) {
                WidgetConfigManager.clearSetting(settingsWidget.getName(), setting.key(), true);

                switch (setting.type()) {
                    case TOGGLE -> setting.setBool().accept(setting.getBool().getAsBoolean());
                    case COLOR -> setting.setColor().accept(setting.getColor().getAsInt());
                    case SLIDER -> setting.setFloat().accept((float) setting.getFloat().getAsDouble());
                    case SECTION -> {
                    }
                }

                if (setting.key().equals(openColorKey)) {
                    openColorKey = null;
                    colorPicker = null;
                }
                if (setting.key().equals(draggingSliderKey)) {
                    draggingSliderKey = null;
                }

                WidgetConfigManager.updateWidget(settingsWidget);
                return true;
            }

            switch (setting.type()) {
                case TOGGLE -> {
                    if (!setting.enabled().getAsBoolean()) {
                        continue;
                    }

                    if (inside(mouseX, mouseY, layout.toggleX, rowY - 2, layout.toggleW, layout.btnH)) {
                        boolean newValue = !setting.getBool().getAsBoolean();
                        setting.setBool().accept(newValue);
                        WidgetConfigManager.updateWidget(settingsWidget);

                        if (!newValue && openColorKey != null) {
                            openColorKey = null;
                            colorPicker = null;
                        }
                        return true;
                    }
                }
                case COLOR -> {
                    if (!setting.enabled().getAsBoolean()) {
                        continue;
                    }

                    if (inside(mouseX, mouseY, layout.btnX, rowY - 2, layout.btnW, layout.btnH)) {
                        int gap = 6;
                        int pickerW = 180;
                        int px = modalX() + MODAL_W + gap;
                        int py = modalY();
                        if (px + pickerW > this.width) {
                            px = modalX() - pickerW - gap;
                        }
                        openColorPicker(setting, px, py, pickerW);
                        return true;
                    }
                }
                case SLIDER -> {
                    if (!setting.enabled().getAsBoolean()) {
                        continue;
                    }

                    if (inside(mouseX, mouseY, layout.sliderBarX, rowY - 1, layout.sliderBarW, 12)) {
                        draggingSliderKey = setting.key();
                        float nextValue = sliderValueForMouse(setting, layout.sliderBarX, layout.sliderBarW, mouseX);
                        setting.setFloat().accept(nextValue);
                        WidgetConfigManager.updateWidget(settingsWidget);
                        return true;
                    }
                }
                case SECTION -> {
                }
            }
        }

        return true;
    }

    private void renderSettingsModal(DrawContext ctx, int mouseX, int mouseY) {
        if (settingsWidget == null) {
            return;
        }

        int x = modalX();
        int y = modalY();
        int modalHeight = modalHeight();

        ctx.fill(0, 0, this.width, this.height, 0x88000000);
        ctx.fill(x, y, x + MODAL_W, y + modalHeight, 0xFF111111);
        drawBorder(ctx, x, y, MODAL_W, modalHeight, 0xFFFFFFFF);

        ctx.drawText(textRenderer, settingsWidget.getName() + " Settings", x + 8, y + 8, 0xFFFFFFFF, false);
        ctx.drawText(textRenderer, "X", x + MODAL_W - 17, y + 6, 0xFFFFFFFF, false);

        SettingsLayout layout = beginSettingsLayout();
        boolean grouped = false;

        for (HudWidget.HudSetting setting : safeSettings(settingsWidget)) {
            int rowY = nextRowY(layout);

            if (setting.type() == HudWidget.HudSetting.Type.SECTION) {
                grouped = true;
                drawSectionRow(ctx, layout, rowY, setting.label());
                continue;
            }

            drawResetButton(ctx, layout.resetX, rowY - 1, mouseX, mouseY);
            int labelX = grouped ? layout.nestedStartX : layout.startX;

            switch (setting.type()) {
                case TOGGLE -> {
                    boolean enabled = setting.enabled().getAsBoolean();
                    boolean value = setting.getBool().getAsBoolean();
                    ctx.drawText(textRenderer, setting.label(), labelX, rowY, enabled ? 0xFFFFFFFF : 0xFF777777, false);
                    drawTogglePill(ctx, layout.toggleX, rowY - 2, layout.toggleW, layout.btnH, value, enabled);
                }
                case COLOR -> {
                    boolean enabled = setting.enabled().getAsBoolean();
                    int color = setting.getColor().getAsInt();
                    ctx.drawText(textRenderer, setting.label(), labelX, rowY, enabled ? 0xFFFFFFFF : 0xFF777777, false);

                    if (enabled) {
                        drawPickButton(ctx, layout.btnX, rowY - 2, layout.btnW, layout.btnH, mouseX, mouseY);
                        drawSwatch(ctx, layout.btnX - 18, rowY - 1, color);
                    } else {
                        ctx.drawText(textRenderer, "-", layout.btnX + 26, rowY, 0xFF777777, false);
                    }
                }
                case SLIDER -> {
                    boolean enabled = setting.enabled().getAsBoolean();
                    float value = (float) setting.getFloat().getAsDouble();
                    ctx.drawText(textRenderer, setting.label(), labelX, rowY, enabled ? 0xFFFFFFFF : 0xFF777777, false);
                    drawSliderRow(ctx, layout, rowY, value, setting.min(), setting.max(), setting.step(), enabled);
                }
                case SECTION -> {
                }
            }
        }

        if (colorPicker != null) {
            int gap = 6;
            int pickerW = colorPicker.getWidth();
            int px = modalX() + MODAL_W + gap;
            int py = modalY();
            if (px + pickerW > this.width) {
                px = modalX() - pickerW - gap;
            }
            colorPicker.setPos(px, py);
            colorPicker.render(ctx, mouseX, mouseY);
        }
    }

    private void drawPickButton(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        int bg = hovered ? 0xFF444444 : 0xFF333333;
        ctx.fill(x, y, x + w, y + h, bg);
        drawBorder(ctx, x, y, w, h, 0xFF000000);
        ctx.drawText(textRenderer, "Pick", x + 18, y + 3, 0xFFFFFFFF, false);
    }

    private void drawSwatch(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x, y, x + 12, y + 12, color);
        drawBorder(ctx, x, y, 12, 12, 0xFF000000);
    }

    private void drawTogglePill(DrawContext ctx, int x, int y, int w, int h, boolean on, boolean enabled) {
        int bg = on ? 0xFF2ECC71 : 0xFF7F8C8D;
        if (!enabled) {
            bg = 0xFF444444;
        }

        ctx.fill(x, y, x + w, y + h, bg);
        drawBorder(ctx, x, y, w, h, 0xFF000000);

        int textColor = enabled ? 0xFF000000 : 0xFF1A1A1A;
        ctx.drawText(textRenderer, on ? "ON" : "OFF", x + 10, y + 3, textColor, false);
    }

    private void drawResetButton(DrawContext ctx, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, RESET_W, RESET_H);
        int bg = hovered ? 0xFF555555 : 0xFF333333;

        ctx.fill(x, y, x + RESET_W, y + RESET_H, bg);
        drawBorder(ctx, x, y, RESET_W, RESET_H, 0xFF000000);
        ctx.drawText(textRenderer, "R", x + 3, y + 2, 0xFFFFFFFF, false);
    }

    private void drawSectionRow(DrawContext ctx, SettingsLayout layout, int rowY, String label) {
        ctx.drawText(textRenderer, label, layout.startX, rowY, 0xFF8EC5FF, false);

        int dividerX = layout.startX + textRenderer.getWidth(label) + 6;
        int dividerY = rowY + (textRenderer.fontHeight / 2);
        if (dividerX < layout.resetX - 4) {
            ctx.drawHorizontalLine(dividerX, layout.resetX - 4, dividerY, 0xFF2D2D2D);
        }
    }

    private void drawSliderRow(DrawContext ctx, SettingsLayout layout, int rowY,
                               float value, float min, float max, float step, boolean enabled) {
        int barX = layout.sliderBarX;
        int barY = rowY - 1;
        int barW = layout.sliderBarW;
        int barH = 12;

        int bg = enabled ? 0xFF2A2A2A : 0xFF1F1F1F;
        ctx.fill(barX, barY, barX + barW, barY + barH, bg);
        drawBorder(ctx, barX, barY, barW, barH, 0xFF000000);

        float t = (max == min) ? 0f : (value - min) / (max - min);
        t = Math.max(0f, Math.min(1f, t));

        int knobX = barX + (int) (t * (barW - 4));
        ctx.fill(knobX, barY, knobX + 4, barY + barH, enabled ? 0xFF7F8C8D : 0xFF444444);

        String displayValue = (step >= 1f) ? String.format("%.0f", value) : String.format("%.2f", value);
        ctx.drawText(textRenderer, displayValue,
                barX + (barW / 2) - (textRenderer.getWidth(displayValue) / 2),
                rowY + 1,
                enabled ? 0xFFAAAAAA : 0xFF777777,
                false
        );
    }

    private float sliderValueForMouse(HudWidget.HudSetting setting, int barX, int barW, double mouseX) {
        float t = (float) ((mouseX - barX) / (double) barW);
        t = Math.max(0f, Math.min(1f, t));

        float raw = setting.min() + t * (setting.max() - setting.min());
        float snapped = (setting.step() > 0f) ? (Math.round(raw / setting.step()) * setting.step()) : raw;
        return Math.max(setting.min(), Math.min(setting.max(), snapped));
    }

    private int modalHeight() {
        int settingsCount = settingsWidget == null ? 0 : safeSettings(settingsWidget).size();
        if (settingsCount == 0) {
            return MODAL_MIN_H;
        }

        int contentHeight = settingsCount * SETTINGS_ROW_H
                + Math.max(0, settingsCount - 1) * SETTINGS_ROW_GAP;
        return Math.max(MODAL_MIN_H, 28 + contentHeight + MODAL_PAD);
    }

    private int modalX() {
        int base = (this.width - MODAL_W) / 2;
        if (colorPicker != null) {
            int pickerW = colorPicker.getWidth();
            int gap = 8;
            return base - (pickerW + gap) / 2;
        }
        return base;
    }

    private int modalY() {
        return (this.height - modalHeight()) / 2;
    }

    private SettingsLayout beginSettingsLayout() {
        settingsRowIndex = 0;

        int x = modalX();
        int y = modalY();

        int startX = x + MODAL_PAD;
        int startY = y + 28;
        int nestedStartX = startX + 10;

        int btnW = 60;
        int btnH = 14;
        int rightEdge = x + MODAL_W - MODAL_PAD;
        int resetX = rightEdge - RESET_W;
        int controlsRight = resetX - 4;
        int btnX = controlsRight - btnW;
        int toggleW = 42;
        int toggleX = controlsRight - toggleW;
        int sliderBarW = 120;
        int sliderBarX = controlsRight - sliderBarW;

        return new SettingsLayout(
                startX, startY, nestedStartX,
                btnW, btnH, btnX,
                toggleW, toggleX,
                resetX,
                sliderBarX, sliderBarW
        );
    }

    private int nextRowY(SettingsLayout layout) {
        int rowY = layout.startY + settingsRowIndex * (SETTINGS_ROW_H + SETTINGS_ROW_GAP);
        settingsRowIndex++;
        return rowY;
    }

    private static List<HudWidget.HudSetting> safeSettings(HudWidget widget) {
        return Optional.ofNullable(widget.getSettings()).orElse(List.of());
    }

    private String iconPathForWidget(HudWidget widget) {
        return switch (widget.getName()) {
            case "Text" -> TEXT_WIDGET_ICON_PATH;
            case "Counter" -> COUNTER_WIDGET_ICON_PATH;
            case "Timer" -> TIMER_WIDGET_ICON_PATH;
            case "Nameplates" -> NAMEPLATES_WIDGET_ICON_PATH;
            case "Better Dialogue" -> BETTER_DIALOGUE_WIDGET_ICON_PATH;
            case "UIToggle" -> UI_TOGGLE_WIDGET_ICON_PATH;
            case "Health Display" -> HEALTH_DISPLAY_WIDGET_ICON_PATH;
            case "Mana Display" -> MANA_DISPLAY_WIDGET_ICON_PATH;
            case "Defense Display" -> DEFENSE_DISPLAY_WIDGET_ICON_PATH;
            case "Cooldown Display" -> COOLDOWN_DISPLAY_WIDGET_ICON_PATH;
            case "Vault Browser" -> VAULT_BROWSER_WIDGET_ICON_PATH;
            default -> "";
        };
    }

    private boolean drawIconIfPresent(DrawContext ctx, String resourcePath, int x, int y, int size) {
        Identifier texture = iconIdentifier(resourcePath);
        if (texture == null) {
            return false;
        }

        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, size, size, size, size);
        return true;
    }

    private Identifier iconIdentifier(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        return Identifier.of(LegendsAddon.MOD_ID, resourcePath);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }

        String clipped = text;
        while (!clipped.isEmpty() && textRenderer.getWidth(clipped + "...") > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped.isEmpty() ? text : clipped + "...";
    }

    private String boundKeyName(KeyBinding keyBinding) {
        return keyBinding == null ? "Unbound" : keyBinding.getBoundKeyLocalizedText().getString();
    }

    private void applyCapturedKeybind(KeybindRow row, InputUtil.Key key) {
        if (row == null) {
            return;
        }

        KeyBinding binding = row.bindingSupplier.get();
        if (binding == null) {
            editingKeybindRow = null;
            return;
        }

        binding.setBoundKey(key);
        KeyBinding.updateKeysByCode();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) {
            client.options.write();
        }

        editingKeybindRow = null;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private static int shadeColor(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int red = clampChannel(Math.round(((color >>> 16) & 0xFF) * factor));
        int green = clampChannel(Math.round(((color >>> 8) & 0xFF) * factor));
        int blue = clampChannel(Math.round((color & 0xFF) * factor));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void drawBorder(DrawContext ctx, int x, int y, int width, int height, int color) {
        ctx.drawVerticalLine(x, y, y + height, color);
        ctx.drawVerticalLine(x + width, y, y + height, color);
        ctx.drawHorizontalLine(x, x + width, y, color);
        ctx.drawHorizontalLine(x, x + width, y + height, color);
    }

    private enum ViewMode {
        HOME,
        MODS,
        LAYOUT,
        GENERAL
    }

    private record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, x, y, width, height);
        }
    }

    private record LauncherLayout(Rect layoutButton, Rect modsButton, Rect settingsButton) {
    }

    private record GridMetrics(int tileSize, int gap, int columns, int rows, int startX, int startY, int gridWidth, int gridHeight) {
    }

    private record WidgetTile(HudWidget widget, Rect bounds, Rect settingsButton) {
    }

    private record KeybindRow(String label, KeyBindingSupplier bindingSupplier) {
    }

    @FunctionalInterface
    private interface KeyBindingSupplier {
        KeyBinding get();
    }

    private record SettingsLayout(int startX, int startY, int nestedStartX,
                                  int btnW, int btnH, int btnX,
                                  int toggleW, int toggleX,
                                  int resetX,
                                  int sliderBarX, int sliderBarW) {
    }

    private record SnapPosition(double x, double y) {
    }

    private record SnapRect(double x, double y, double width, double height) {
        double left() {
            return x;
        }

        double centerX() {
            return x + width / 2d;
        }

        double right() {
            return x + width;
        }

        double top() {
            return y;
        }

        double centerY() {
            return y + height / 2d;
        }

        double bottom() {
            return y + height;
        }
    }
}
