package legends.ultra.cool.addons.hud;

import legends.ultra.cool.addons.LegendsAddon;
import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.widget.settings.ColorPicker;
import legends.ultra.cool.addons.hud.widget.settings.CustomListFormScreen;
import legends.ultra.cool.addons.input.Keybinds;
import legends.ultra.cool.addons.util.AddonServerGate;
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
import java.util.Objects;
import java.util.Optional;

public class HudEditorScreen extends Screen {
    private static final String EDITOR_CONFIG_ID = "__hud_editor__";
    private static final String GENERAL_SETTINGS_CONFIG_ID = "__general_settings__";
    private static final String SNAP_ENABLED_KEY = "snapEnabled";
    private static final String THEME_KEY = "editorTheme";

    private static final int SNAP_DISTANCE = 6;
    private static final int SNAP_GUIDE_COLOR = 0xCC4AA3DF;
    private static final int HOTBAR_GUIDE_COLOR = 0x664AA3DF;
    private static final int HOTBAR_WIDTH = 181;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_SLOT_SIZE = 20;
    private static final int HOTBAR_SLOT_OFFSET_X = 1;
    private static final int HOTBAR_SLOT_OFFSET_Y = 1;
    private static final int HOTBAR_SLOT_COUNT = 9;

    private static final int MODAL_MIN_W = 220;
    private static final int MODAL_MIN_H = 180;
    private static final int MODAL_PAD = 8;
    private static final int MODAL_LABEL_CONTROL_GAP = 8;
    private static final int SETTINGS_ROW_H = 16;
    private static final int SETTINGS_ROW_GAP = 6;
    private static final int RESET_W = 12;
    private static final int RESET_H = 12;
    private static final int CUSTOM_ADD_W = 42;
    private static final int CUSTOM_ENTRY_H = 28;
    private static final int CUSTOM_ENTRY_GAP = 4;
    private static final int SETTINGS_SCROLL_STEP = 24;
    private static final int SETTINGS_CONTROL_W = 60;
    private static final int SETTINGS_CONTROL_H = 14;
    private static final int SETTINGS_TOGGLE_W = 42;
    private static final int SETTINGS_SLIDER_W = 120;
    private static final int SETTINGS_RESET_GAP = 4;
    private static final int SETTINGS_COLOR_SWATCH_W = 18;
    private static final int SETTINGS_DROPDOWN_OPTION_H = 16;
    private static final int SETTINGS_DROPDOWN_GAP = 2;

    private static final int LAUNCHER_SQUARE = 40;
    private static final int LAUNCHER_WIDE = 100;
    private static final int LAUNCHER_GAP = 10;
    private static final int CHIP_H = 18;
    private static final int BACK_W = 18;
    private static final int SNAP_W = 70;
    private static final int MODS_VIEW_TOP = 42;
    private static final int MODS_VIEW_BOTTOM = 18;
    private static final int MODS_SCROLL_STEP = 28;

    private static final int MOD_TILE_SIZE = 96;
    private static final int MOD_TILE_MIN_SIZE = 72;
    private static final int MOD_TILE_GAP = 14;
    private static final int MOD_TILE_ICON_SIZE = 54;
    private static final int LAUNCHER_ICON_SIZE = 12;
    private static final int GENERAL_TAB_H = 16;
    private static final int GENERAL_TAB_GAP = 12;
    private static final int GENERAL_PANEL_MIN_H = 188;
    private static final int GENERAL_KEYBIND_START_Y = 66;
    private static final int GENERAL_KEYBIND_ROW_H = 18;
    private static final int GENERAL_KEYBIND_ROW_SPAN = 24;
    private static final int GENERAL_KEYBIND_FOOTER_SPACE = 24;
    private static final int GENERAL_ROW_H = 26;
    private static final int GENERAL_TOGGLE_W = 48;
    private static final int GENERAL_TOGGLE_H = 16;
    private static final int GENERAL_DROPDOWN_W = 136;
    private static final int GENERAL_THEME_OPTION_H = 18;
    private static final int GENERAL_DROPDOWN_GAP = 4;
    private static final Identifier VANILLA_BUTTON_SPRITE = Identifier.ofVanilla("widget/button");
    private static final Identifier VANILLA_BUTTON_HOVERED_SPRITE = Identifier.ofVanilla("widget/button_highlighted");
    private static final Identifier VANILLA_BUTTON_DISABLED_SPRITE = Identifier.ofVanilla("widget/button_disabled");
    private static final Identifier VANILLA_TEXT_FIELD_SPRITE = Identifier.ofVanilla("widget/text_field");
    private static final Identifier VANILLA_TEXT_FIELD_HIGHLIGHTED_SPRITE = Identifier.ofVanilla("widget/text_field_highlighted");
    private static final Identifier VANILLA_POPUP_BACKGROUND_SPRITE = Identifier.ofVanilla("popup/background");
    private static final Identifier VANILLA_SCROLLER_BACKGROUND_SPRITE = Identifier.ofVanilla("widget/scroller_background");
    private static final Identifier VANILLA_SCROLLER_SPRITE = Identifier.ofVanilla("widget/scroller");
    private static final Identifier VANILLA_MENU_BACKGROUND_TEXTURE = Identifier.ofVanilla("textures/gui/menu_background.png");

    private int UI_PANEL_COLOR = 0xFF111111;
    private int UI_PANEL_HOVER_COLOR = 0xFF1A1A1A;
    private int UI_BORDER_COLOR = 0xFFFFFFFF;
    private int UI_BORDER_DARK = 0xFF000000;
    private int UI_BUTTON_TEXT_COLOR = 0xFFFFFFFF;
    private int UI_SUBTEXT_COLOR = 0xFFAAAAAA;
    private int MOD_TILE_ENABLED_COLOR = 0xFF26C665;
    private int MOD_TILE_ENABLED_FOOTER = 0xFF1D8E49;
    private int MOD_TILE_DISABLED_COLOR = 0xFF111111;
    private int MOD_TILE_DISABLED_FOOTER = 0xFF0A0A0A;
    private int MOD_TILE_DISABLED_BORDER = 0xFF000000;
    private int BUTTON_FILL_COLOR = 0xFF333333;
    private int BUTTON_FILL_HOVER_COLOR = 0xFF444444;
    private int BUTTON_BORDER_COLOR = 0xFFFFFFFF;
    private int BUTTON_BORDER_HOVER_COLOR = 0xFFFFFFFF;
    private int BACKDROP_TOP_COLOR = 0x88000000;
    private int BACKDROP_BOTTOM_COLOR = 0xAA000000;
    private int LAYOUT_BACKDROP_TOP_COLOR = 0x44000000;
    private int LAYOUT_BACKDROP_BOTTOM_COLOR = 0x66000000;
    private int DIVIDER_COLOR = 0x66000000;
    private int SCROLL_TRACK_COLOR = 0x66000000;
    private int SCROLL_THUMB_COLOR = 0xCCFFFFFF;
    private int MODAL_OVERLAY_COLOR = 0x88000000;
    private int MODAL_PANEL_COLOR = 0xFF111111;
    private int MODAL_BORDER_COLOR = 0xFFFFFFFF;
    private int MODAL_TEXT_COLOR = 0xFFFFFFFF;
    private int MODAL_DISABLED_TEXT_COLOR = 0xFF777777;
    private int MODAL_SECTION_COLOR = 0xFF8EC5FF;
    private int MODAL_SECTION_DIVIDER_COLOR = 0xFF2D2D2D;
    private int MODAL_CONTROL_FILL_COLOR = 0xFF333333;
    private int MODAL_CONTROL_HOVER_COLOR = 0xFF444444;
    private int MODAL_TOGGLE_ON_COLOR = 0xFF2ECC71;
    private int MODAL_TOGGLE_OFF_COLOR = 0xFF7F8C8D;
    private int MODAL_DISABLED_FILL_COLOR = 0xFF444444;
    private int MODAL_VALUE_TEXT_COLOR = 0xFFAAAAAA;
    private int SLIDER_FILL_COLOR = 0xFF2A2A2A;
    private int SLIDER_DISABLED_FILL_COLOR = 0xFF1F1F1F;
    private int SLIDER_KNOB_COLOR = 0xFF7F8C8D;
    private int SLIDER_KNOB_DISABLED_COLOR = 0xFF444444;

    // Resource paths are relative to assets/legends-addon/. Leave blank until the texture exists.
    private static final String LAYOUT_BUTTON_ICON_PATH = "textures/icon/layout_icon.png";
    private static final String GENERAL_SETTINGS_BUTTON_ICON_PATH = "textures/icon/settings_icon.png";
    private static final String TEXT_WIDGET_ICON_PATH = "";
    private static final String COUNTER_WIDGET_ICON_PATH = "";
    private static final String TIMER_WIDGET_ICON_PATH = "textures/icon/stopwatch_icon.png";
    private static final String NAMEPLATES_WIDGET_ICON_PATH = "textures/icon/nameplate_icon.png";
    private static final String UI_TOGGLE_WIDGET_ICON_PATH = "textures/icon/ui_toggle_icon.png";
    private static final String HEALTH_DISPLAY_WIDGET_ICON_PATH = "textures/icon/health_display_icon.png";
    private static final String MANA_DISPLAY_WIDGET_ICON_PATH = "textures/icon/mana_display_icon.png";
    private static final String DEFENSE_DISPLAY_WIDGET_ICON_PATH = "textures/icon/defense_display_icon.png";
    private static final String COOLDOWN_DISPLAY_WIDGET_ICON_PATH = "textures/icon/cooldown_display_icon.png";
    private static final String VAULT_BROWSER_WIDGET_ICON_PATH = "textures/icon/vault_browser_icon.png";

    private final List<KeybindRow> keybindRows = List.of(
            new KeybindRow("Open editor", () -> Keybinds.OPEN_EDITOR),
            new KeybindRow("Toggle timer", () -> Keybinds.TOGGLE_TIMER),
            new KeybindRow("Reset timer", () -> Keybinds.RESET_TIMER),
            new KeybindRow("Open Vault", () -> Keybinds.OPEN_VAULT),
            new KeybindRow("Open Wardrobe", () -> Keybinds.OPEN_WARDROBE)
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
    private String openDropdownKey;
    private String draggingSliderKey;
    private KeybindRow editingKeybindRow;
    private GeneralTab activeGeneralTab = GeneralTab.LOOKS_AND_FEEL;
    private boolean themeDropdownOpen = false;
    private EditorTheme activeTheme = EditorTheme.DEFAULT;
    private int settingsCursorY = 0;
    private int settingsScroll = 0;
    private int settingsMaxScroll = 0;

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
        snapEnabled = WidgetConfigManager.getBool(EDITOR_CONFIG_ID, SNAP_ENABLED_KEY, true);
        applyTheme(EditorTheme.fromId(WidgetConfigManager.getString(GENERAL_SETTINGS_CONFIG_ID, THEME_KEY, EditorTheme.DEFAULT.id)));
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
                if (openDropdownKey != null) {
                    openDropdownKey = null;
                    return true;
                }

                closeSettingsModal();
                return true;
            }

            if (themeDropdownOpen) {
                themeDropdownOpen = false;
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

        snapEnabled = button != 1;

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
                    int rowY = nextSettingY(layout, setting);
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
        if (isSettingsOpen()) {
            updateSettingsScrollBounds();
            if (settingsMaxScroll > 0 && settingsViewportBounds().contains(mouseX, mouseY)) {
                openDropdownKey = null;
                settingsScroll = MathHelper.clamp(
                        settingsScroll - (int) Math.round(verticalAmount * SETTINGS_SCROLL_STEP),
                        0,
                        settingsMaxScroll
                );
            }
            return true;
        }

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

        if (viewMode == ViewMode.LAYOUT) {
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
        if (backChipBounds().contains(mouseX, mouseY)) {
            close();
            return true;
        }

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

        for (GeneralTab tab : GeneralTab.values()) {
            if (generalTabBounds(tab).contains(mouseX, mouseY)) {
                activeGeneralTab = tab;
                editingKeybindRow = null;
                themeDropdownOpen = false;
                return true;
            }
        }

        if (activeGeneralTab == GeneralTab.LOOKS_AND_FEEL) {
            if (themeDropdownButtonBounds().contains(mouseX, mouseY)) {
                themeDropdownOpen = !themeDropdownOpen;
                return true;
            }

            if (themeDropdownOpen) {
                for (int index = 0; index < EditorTheme.values().length; index++) {
                    if (themeOptionBounds(index).contains(mouseX, mouseY)) {
                        applyTheme(EditorTheme.values()[index]);
                        WidgetConfigManager.setString(GENERAL_SETTINGS_CONFIG_ID, THEME_KEY, activeTheme.id, true);
                        themeDropdownOpen = false;
                        return true;
                    }
                }
            }

            if (serverToggleButtonBounds().contains(mouseX, mouseY)) {
                AddonServerGate.setLegendsOnlyModeEnabled(!AddonServerGate.isLegendsOnlyModeEnabled());
                themeDropdownOpen = false;
                return true;
            }

            themeDropdownOpen = false;
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
        if (useMinecraftTheme()) {
            renderMinecraftMenuBackground(ctx);
            return;
        }

        int top = viewMode == ViewMode.LAYOUT ? LAYOUT_BACKDROP_TOP_COLOR : BACKDROP_TOP_COLOR;
        int bottom = viewMode == ViewMode.LAYOUT ? LAYOUT_BACKDROP_BOTTOM_COLOR : BACKDROP_BOTTOM_COLOR;
        ctx.fillGradient(0, 0, this.width, this.height, top, bottom);
    }

    private void renderHome(DrawContext ctx, int mouseX, int mouseY) {
        LauncherLayout launcher = launcherLayout();

        drawChip(ctx, backChipBounds(),  mouseX, mouseY, "x", BUTTON_FILL_HOVER_COLOR, UI_BUTTON_TEXT_COLOR);

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("HUD Editor"), this.width / 2, launcher.modsButton.y - 24, UI_BUTTON_TEXT_COLOR);

        drawLauncherCard(ctx, launcher.layoutButton, mouseX, mouseY, "", LAYOUT_BUTTON_ICON_PATH, false);
        drawLauncherCard(ctx, launcher.modsButton, mouseX, mouseY, "Mods", null, true);
        drawLauncherCard(ctx, launcher.settingsButton, mouseX, mouseY, "", GENERAL_SETTINGS_BUTTON_ICON_PATH, false);
    }

    private void renderMods(DrawContext ctx, int mouseX, int mouseY) {
        Rect viewport = modsViewportBounds();
        GridMetrics grid = computeUnscrolledGridMetrics(HudManager.getWidgets().size(), viewport);

        int paddingTop = 33;
        int padding = 15;
        int startY = grid.startY;
        int startX = grid.startX;
        int totalHeight = grid.gridHeight;
        int totalWidth = grid.gridWidth;

        Rect panel = new Rect(startX - padding, startY - paddingTop, totalWidth + 2*padding, totalHeight + padding + paddingTop);

        drawPanel(ctx, panel, UI_PANEL_COLOR, UI_PANEL_COLOR, UI_BORDER_COLOR);

        if (useMinecraftTheme()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Mods"), panel.x + panel.width / 2, panel.y + 14, UI_BUTTON_TEXT_COLOR);
        } else {
            ctx.drawText(textRenderer, "Mods", panel.x + 18, panel.y + 14, UI_BUTTON_TEXT_COLOR, false);
        }

        drawChip(ctx, backChipBounds(),  mouseX, mouseY, "x", BUTTON_FILL_HOVER_COLOR, UI_BUTTON_TEXT_COLOR);

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
        drawChip(ctx, backChipBounds(),  mouseX, mouseY, "x", BUTTON_FILL_HOVER_COLOR, UI_BUTTON_TEXT_COLOR);

        String hint = "Drag enabled widgets. Press ESC to return.";
        String hint2 = "Hold right click to disable snap";
        int hintX = this.width - textRenderer.getWidth(hint) - 10;
        int hint2X = this.width - textRenderer.getWidth(hint2) - 10;
        ctx.drawText(textRenderer, hint, Math.max(14, hintX), this.height - 14, UI_SUBTEXT_COLOR, false);
        ctx.drawText(textRenderer, hint2, Math.max(14, hint2X), this.height - 27, UI_SUBTEXT_COLOR, false);
    }

    private void renderGeneral(DrawContext ctx, int mouseX, int mouseY) {
        Rect panel = generalPanelBounds();

        drawPanel(ctx, panel, UI_PANEL_COLOR, UI_PANEL_COLOR, UI_BORDER_COLOR);


        if (useMinecraftTheme()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("General Settings"), panel.x + panel.width / 2, panel.y + 14, UI_BUTTON_TEXT_COLOR);
        } else {
            ctx.drawText(textRenderer, "General Settings", panel.x + 18, panel.y + 14, UI_BUTTON_TEXT_COLOR, false);
        }

        renderGeneralTab(ctx, generalTabBounds(GeneralTab.LOOKS_AND_FEEL), "Looks & Feel", activeGeneralTab == GeneralTab.LOOKS_AND_FEEL, mouseX, mouseY);
        renderGeneralTab(ctx, generalTabBounds(GeneralTab.KEYBINDS), "Keybinds", activeGeneralTab == GeneralTab.KEYBINDS, mouseX, mouseY);

        if (activeGeneralTab == GeneralTab.LOOKS_AND_FEEL) {
            renderLooksAndFeel(ctx, panel, mouseX, mouseY);
            return;
        }

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
        if (useMinecraftTheme()) {
            drawMinecraftButton(ctx, rect, hovered, false);

            if (centerLabelOnly) {
                drawMinecraftCenteredLabel(ctx, rect, label, hovered);
                return;
            }

            int labelHeight = label.isBlank() ? 0 : textRenderer.fontHeight;
            int iconAreaHeight = Math.max(LAUNCHER_ICON_SIZE, rect.height - labelHeight);
            int iconX = rect.x + (rect.width - LAUNCHER_ICON_SIZE) / 2;
            int iconY = rect.y + Math.max(0, (iconAreaHeight - LAUNCHER_ICON_SIZE) / 2);
            int labelY = rect.bottom() - 14;

            if (drawIconIfPresent(ctx, iconPath, iconX, iconY, LAUNCHER_ICON_SIZE)) {
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), rect.x + rect.width / 2, labelY, minecraftTextColor(hovered, false));
                return;
            }

            drawMinecraftCenteredLabel(ctx, rect, label, hovered);
            return;
        }

        int top = hovered ? UI_PANEL_HOVER_COLOR : UI_PANEL_COLOR;
        int bottom = top;
        int border = hovered ? BUTTON_BORDER_HOVER_COLOR : BUTTON_BORDER_COLOR;

        drawPanel(ctx, rect, top, bottom, border);

        if (centerLabelOnly) {

            float scale = 1.5f;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().scale(scale, scale);

            ctx.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal(label),
                    (int) ((rect.x + rect.width / 2) / scale),
                    (int) ((rect.y + (rect.height - textRenderer.fontHeight) / 2) / scale),
                    UI_BUTTON_TEXT_COLOR);

            ctx.getMatrices().popMatrix();
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

        if (useMinecraftTheme()) {
            ctx.fill(tile.bounds.x, tile.bounds.y, tile.bounds.right(), footerTop, bodyColor);
            ctx.fill(tile.bounds.x, footerTop, footerRight, tile.bounds.bottom(), footerColor);
            if (hasSettings) {
                drawMinecraftButton(ctx, tile.settingsButton, settingsHovered, false);
            }

            drawIconIfPresent(
                    ctx,
                    iconPathForWidget(tile.widget),
                    tile.bounds.x + (tile.bounds.width - MOD_TILE_ICON_SIZE) / 2,
                    tile.bounds.y + 18,
                    MOD_TILE_ICON_SIZE
            );

            footerTop = tile.bounds.bottom() - tile.settingsButton.height;
            footerRight = hasSettings ? tile.settingsButton.x : tile.bounds.right();
            renderScrollingTileLabel(
                    ctx,
                    tile.widget.getName(),
                    tile.bounds.x + 6,
                    footerTop + 5,
                    footerRight - tile.bounds.x - 12,
                    tile.settingsButton.height - 2
            );

            if (hasSettings) {
                drawMiniSettingsIcon(ctx, tile.settingsButton);
            }
            return;
        }


        ctx.fill(tile.bounds.x, tile.bounds.y, tile.bounds.right(), footerTop, bodyColor);
        ctx.fill(tile.bounds.x, footerTop, footerRight, tile.bounds.bottom(), footerColor);
        if (hasSettings) {
            ctx.fill(tile.settingsButton.x, footerTop, tile.settingsButton.right(), tile.settingsButton.bottom(), settingsColor);
        }
        drawBorder(ctx, tile.bounds.x, tile.bounds.y, tile.bounds.width, tile.bounds.height, bodyBorder);
        ctx.drawHorizontalLine(tile.bounds.x, tile.bounds.right(), footerTop, DIVIDER_COLOR);
        if (hasSettings) {
            ctx.drawVerticalLine(tile.settingsButton.x, footerTop, tile.settingsButton.bottom(), DIVIDER_COLOR);
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

        if (useMinecraftTheme()) {
            ctx.drawText(textRenderer, row.label, rowBounds.x, rowBounds.y + 5, UI_BUTTON_TEXT_COLOR, false);
            drawMinecraftTextField(ctx, valueBounds, editing || valueHovered);
            String value = editing ? "Press key..." : boundKeyName(row.bindingSupplier.get());
            ctx.drawText(textRenderer, trimToWidth(value, valueBounds.width - 10), valueBounds.x + 5, rowBounds.y + 5, minecraftTextColor(editing || valueHovered, false), false);
            return;
        }

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

    private void renderGeneralTab(DrawContext ctx, Rect rect, String label, boolean active, int mouseX, int mouseY) {
        drawChip(ctx, backChipBounds(),  mouseX, mouseY, "x", BUTTON_FILL_HOVER_COLOR, UI_BUTTON_TEXT_COLOR);

        boolean hovered = rect.contains(mouseX, mouseY);
        if (useMinecraftTheme()) {
            drawMinecraftButton(ctx, rect, hovered || active, false);
            drawMinecraftCenteredLabel(ctx, rect, label, hovered || active);
            return;
        }



        int textColor = active ? UI_BUTTON_TEXT_COLOR : (hovered ? UI_BUTTON_TEXT_COLOR : UI_SUBTEXT_COLOR);
        ctx.drawText(
                textRenderer,
                label,
                rect.x,
                rect.y + 4,
                textColor,
                false
        );
        if (active) {
            ctx.fill(rect.x, rect.bottom() - 1, rect.right(), rect.bottom(), UI_BORDER_COLOR);
        }
    }

    private void renderLooksAndFeel(DrawContext ctx, Rect panel, int mouseX, int mouseY) {
        Rect rowBounds = serverToggleRowBounds();
        Rect valueBounds = serverToggleButtonBounds();
        boolean valueHovered = valueBounds.contains(mouseX, mouseY);
        boolean enabled = AddonServerGate.isLegendsOnlyModeEnabled();

        ctx.drawText(textRenderer, "LegendsRPG only", rowBounds.x, rowBounds.y + 2, UI_BUTTON_TEXT_COLOR, false);

        if (useMinecraftTheme()) {
            drawMinecraftButton(ctx, valueBounds, valueHovered || enabled, false);
        } else {
            int valueFill = enabled
                    ? BUTTON_FILL_HOVER_COLOR
                    : (valueHovered ? BUTTON_FILL_HOVER_COLOR : BUTTON_FILL_COLOR);
            int valueBorder = enabled ? UI_BORDER_COLOR : UI_BORDER_DARK;
            ctx.fill(valueBounds.x, valueBounds.y, valueBounds.right(), valueBounds.bottom(), valueFill);
            drawBorder(ctx, valueBounds.x, valueBounds.y, valueBounds.width, valueBounds.height, valueBorder);
        }
        ctx.drawText(
                textRenderer,
                enabled ? "On" : "Off",
                valueBounds.x + Math.max(0, (valueBounds.width - textRenderer.getWidth(enabled ? "On" : "Off")) / 2),
                valueBounds.y + 4,
                useMinecraftTheme() ? minecraftTextColor(valueHovered || enabled, false) : UI_BUTTON_TEXT_COLOR,
                false
        );

        String currentServer = AddonServerGate.getCurrentServerAddress();
        String status;
        if (!enabled) {
            status = "Runs everywhere.";
        } else if (AddonServerGate.isOnLegendsServer()) {
            status = "Active here.";
        } else if (currentServer.isBlank()) {
            status = "Blocked in singleplayer.";
        } else {
            status = "Blocked on " + currentServer + ".";
        }
        ctx.drawText(textRenderer, status, rowBounds.x, rowBounds.y + 14, UI_SUBTEXT_COLOR, false);

        Rect themeRowBounds = themeRowBounds();
        Rect themeButtonBounds = themeDropdownButtonBounds();
        boolean themeHovered = themeButtonBounds.contains(mouseX, mouseY);

        ctx.drawText(textRenderer, "Theme", themeRowBounds.x, themeRowBounds.y + 2, UI_BUTTON_TEXT_COLOR, false);
        if (useMinecraftTheme()) {
            drawMinecraftButton(ctx, themeButtonBounds, themeDropdownOpen || themeHovered, false);
        } else {
            ctx.fill(
                    themeButtonBounds.x,
                    themeButtonBounds.y,
                    themeButtonBounds.right(),
                    themeButtonBounds.bottom(),
                    themeDropdownOpen || themeHovered ? BUTTON_FILL_HOVER_COLOR : BUTTON_FILL_COLOR
            );
            drawBorder(
                    ctx,
                    themeButtonBounds.x,
                    themeButtonBounds.y,
                    themeButtonBounds.width,
                    themeButtonBounds.height,
                    themeDropdownOpen ? UI_BORDER_COLOR : UI_BORDER_DARK
            );
        }

        String themeLabel = trimToWidth(activeTheme.label, themeButtonBounds.width - 18);
        ctx.drawText(textRenderer, themeLabel, themeButtonBounds.x + 6, themeButtonBounds.y + 4, useMinecraftTheme() ? minecraftTextColor(themeDropdownOpen || themeHovered, false) : UI_BUTTON_TEXT_COLOR, false);
        ctx.drawText(textRenderer, themeDropdownOpen ? "▲" : "▼", themeButtonBounds.right() - 9, themeButtonBounds.y + 4, useMinecraftTheme() ? minecraftTextColor(themeDropdownOpen || themeHovered, false) : UI_SUBTEXT_COLOR, false);

        if (themeDropdownOpen) {
            renderThemeDropdown(ctx, mouseX, mouseY);
        }
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

        if (useMinecraftTheme()) {
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, VANILLA_SCROLLER_BACKGROUND_SPRITE, trackX - 2, trackY, 8, trackHeight);
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, VANILLA_SCROLLER_SPRITE, trackX - 2, thumbY, 8, thumbHeight);
            return;
        }

        ctx.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight, SCROLL_TRACK_COLOR);
        ctx.fill(trackX, thumbY, trackX + trackWidth, thumbY + thumbHeight, SCROLL_THUMB_COLOR);
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
        if (useMinecraftTheme()) {
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, VANILLA_POPUP_BACKGROUND_SPRITE, rect.x, rect.y, rect.width, rect.height);
            return;
        }

        ctx.fill(rect.x, rect.y, rect.right(), rect.bottom(), topColor);
        drawBorder(ctx, rect.x, rect.y, rect.width, rect.height, borderColor);
    }

    private void drawChip(DrawContext ctx, Rect rect, int mouseX, int mouseY, String label, int fillColor, int textColor) {
        boolean hovered = rect.contains(mouseX, mouseY);
        if (useMinecraftTheme()) {
            drawMinecraftButton(ctx, backChipBounds() , hovered, false);
        } else {

            if (hovered) {
                ctx.fill(backChipBounds().x, backChipBounds().y, backChipBounds().x + backChipBounds().width, backChipBounds().y + backChipBounds().height, BUTTON_FILL_HOVER_COLOR);
                drawBorder(ctx, backChipBounds().x, backChipBounds().y, backChipBounds().width, backChipBounds().height, BUTTON_BORDER_HOVER_COLOR);
            } else {
                ctx.fill(backChipBounds().x, backChipBounds().y, backChipBounds().x + backChipBounds().width, backChipBounds().y + backChipBounds().height, BUTTON_FILL_COLOR);
                drawBorder(ctx, backChipBounds().x, backChipBounds().y, backChipBounds().width, backChipBounds().height, BUTTON_BORDER_COLOR);
            }
        }
        drawMinecraftCenteredLabel(ctx, backChipBounds(), "x", hovered);}

    private LauncherLayout launcherLayout() {
        int launcherSquare = LAUNCHER_SQUARE;
        if (useMinecraftTheme()) launcherSquare = LAUNCHER_SQUARE - 20;
        int totalWidth = launcherSquare + LAUNCHER_GAP + LAUNCHER_WIDE + LAUNCHER_GAP + launcherSquare;
        int startX = (this.width - totalWidth) / 2;
        int y = (this.height - launcherSquare) / 2;

        Rect layout = new Rect(startX, y, launcherSquare, launcherSquare);
        Rect mods = new Rect(layout.right() + LAUNCHER_GAP, y, LAUNCHER_WIDE, launcherSquare);
        Rect settings = new Rect(mods.right() + LAUNCHER_GAP, y, launcherSquare, launcherSquare);
        return new LauncherLayout(layout, mods, settings);
    }

    private Rect modsViewportBounds() {
        return new Rect(0, MODS_VIEW_TOP, this.width, Math.max(0, this.height - MODS_VIEW_TOP - MODS_VIEW_BOTTOM));
    }

    private Rect generalPanelBounds() {
        int panelWidth = 388;
        int keybindContentHeight = GENERAL_KEYBIND_START_Y
                + Math.max(0, keybindRows.size() - 1) * GENERAL_KEYBIND_ROW_SPAN
                + GENERAL_KEYBIND_ROW_H
                + GENERAL_KEYBIND_FOOTER_SPACE;
        int panelHeight = Math.max(GENERAL_PANEL_MIN_H, keybindContentHeight);
        return new Rect((this.width - panelWidth) / 2, (this.height - panelHeight) / 2, panelWidth, panelHeight);
    }

    private Rect generalTabBounds(GeneralTab tab) {
        Rect panel = generalPanelBounds();
        Rect looks = new Rect(panel.x + 18, panel.y + 36, 72, GENERAL_TAB_H);
        Rect keybinds = new Rect(looks.right() + GENERAL_TAB_GAP, looks.y, 52, GENERAL_TAB_H);
        return tab == GeneralTab.LOOKS_AND_FEEL ? looks : keybinds;
    }

    private Rect serverToggleRowBounds() {
        Rect panel = generalPanelBounds();
        return new Rect(panel.x + 18, panel.y + 98, panel.width - 36, GENERAL_ROW_H);
    }

    private Rect serverToggleButtonBounds() {
        Rect rowBounds = serverToggleRowBounds();
        int valueX = rowBounds.right() - GENERAL_TOGGLE_W;
        int valueY = rowBounds.y + (rowBounds.height - GENERAL_TOGGLE_H) / 2;
        return new Rect(valueX, valueY, GENERAL_TOGGLE_W, GENERAL_TOGGLE_H);
    }

    private Rect themeRowBounds() {
        Rect panel = generalPanelBounds();
        return new Rect(panel.x + 18, panel.y + 66, panel.width - 36, GENERAL_ROW_H);
    }

    private Rect themeDropdownButtonBounds() {
        Rect rowBounds = themeRowBounds();
        int valueX = rowBounds.right() - GENERAL_DROPDOWN_W;
        int valueY = rowBounds.y + (rowBounds.height - GENERAL_TOGGLE_H) / 2;
        return new Rect(valueX, valueY, GENERAL_DROPDOWN_W, GENERAL_TOGGLE_H);
    }

    private Rect themeDropdownListBounds() {
        Rect button = themeDropdownButtonBounds();
        int listHeight = themeDropdownContentHeight();
        int belowY = button.bottom() + GENERAL_DROPDOWN_GAP;
        int aboveY = button.y - GENERAL_DROPDOWN_GAP - listHeight;

        int y = belowY;
        if (belowY + listHeight > this.height - 8 && aboveY >= 8) {
            y = aboveY;
        } else if (belowY + listHeight > this.height - 8) {
            y = Math.max(8, this.height - 8 - listHeight);
        }

        return new Rect(button.x, y, button.width, listHeight);
    }

    private Rect themeOptionBounds(int index) {
        Rect list = themeDropdownListBounds();
        int optionWidth = list.width - 2;
        int optionY = list.y + index * themeDropdownOptionSpan() + 1;
        return new Rect(list.x + 1, optionY, Math.max(1, optionWidth), themeDropdownOptionHeight());
    }

    private int themeDropdownContentHeight() {
        return EditorTheme.values().length * themeDropdownOptionSpan();
    }

    private int themeDropdownOptionSpan() {
        return useMinecraftTheme() ? 22 : GENERAL_THEME_OPTION_H;
    }

    private int themeDropdownOptionHeight() {
        return useMinecraftTheme() ? 20 : GENERAL_THEME_OPTION_H - 1;
    }

    private Rect keybindRowBounds(int index) {
        Rect panel = generalPanelBounds();
        int rowX = panel.x + 18;
        int rowY = panel.y + GENERAL_KEYBIND_START_Y + index * GENERAL_KEYBIND_ROW_SPAN;
        return new Rect(rowX, rowY, panel.width - 36, GENERAL_KEYBIND_ROW_H);
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
        if (viewMode == ViewMode.MODS) {
            Rect viewport = modsViewportBounds();
            GridMetrics grid = computeUnscrolledGridMetrics(HudManager.getWidgets().size(), viewport);

            int padding = 5;
            int startY = grid.startY;
            int startX = grid.startX;
            int totalWidth = grid.gridWidth;

            return new Rect(totalWidth + startX - BACK_W,  startY - CHIP_H - padding, BACK_W, CHIP_H);
        }
        else if (viewMode == ViewMode.GENERAL) {
            Rect viewport = generalPanelBounds();
            GridMetrics grid = computeUnscrolledGridMetrics(HudManager.getWidgets().size(), viewport);

            int padding = 8;
            int startY = grid.startY;
            int startX = grid.startX;
            int totalWidth = grid.gridWidth;

            return new Rect(totalWidth + startX - 2 * BACK_W - padding -1,  startY + padding, BACK_W, CHIP_H);
        }
        return new Rect(this.width - BACK_W - 14,  14, BACK_W, CHIP_H);
    }

    private Rect snapChipBounds() {
        return new Rect(backChipBounds().right() + 8, 14, SNAP_W, CHIP_H);
    }

    private void openWidgetSettings(HudWidget widget) {
        settingsWidget = widget;
        colorPicker = null;
        openColorKey = null;
        openDropdownKey = null;
        draggingSliderKey = null;
        settingsScroll = 0;
        updateSettingsScrollBounds();
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
        openDropdownKey = null;
        draggingSliderKey = null;
        settingsScroll = 0;
        settingsMaxScroll = 0;
    }

    private void setViewMode(ViewMode nextMode) {
        viewMode = nextMode;
        if (viewMode != ViewMode.LAYOUT) {
            clearDragState();
        }
        if (viewMode != ViewMode.GENERAL) {
            editingKeybindRow = null;
            themeDropdownOpen = false;
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
        double objectOffsetX = dragging.getVisualX() - dragging.x;
        double objectOffsetY = dragging.getVisualY() - dragging.y;
        double objectWidth = dragging.getVisualWidth();
        double objectHeight = dragging.getVisualHeight();

        if (snapEnabled) {
            SnapPosition snapped = snapPosition(
                    targetX,
                    targetY,
                    objectOffsetX,
                    objectOffsetY,
                    objectWidth,
                    objectHeight,
                    dragging,
                    null
            );
            targetX = snapped.x;
            targetY = snapped.y;
        } else {
            clearSnapGuides();
        }

        dragging.x = clamp(targetX, -objectOffsetX, this.width - objectOffsetX - objectWidth);
        dragging.y = clamp(targetY, -objectOffsetY, this.height - objectOffsetY - objectHeight);
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
                double widgetWidth = widget.getVisualWidth();
                double widgetHeight = widget.getVisualHeight();
                if (widgetWidth > 0 && widgetHeight > 0) {
                    targets.add(new SnapRect(widget.getVisualX(), widget.getVisualY(), widgetWidth, widgetHeight));
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

    private boolean handleOpenSettingsDropdownClick(double mouseX, double mouseY) {
        if (openDropdownKey == null) {
            return false;
        }

        SettingsLayout layout = beginSettingsLayout();
        for (HudWidget.HudSetting setting : safeSettings(settingsWidget)) {
            int rowY = nextSettingY(layout, setting);
            if (setting.type() != HudWidget.HudSetting.Type.DROPDOWN || !setting.key().equals(openDropdownKey)) {
                continue;
            }

            Rect button = settingsDropdownButtonBounds(layout, rowY);
            if (button.contains(mouseX, mouseY)) {
                return false;
            }

            Rect list = settingsDropdownListBounds(layout, rowY, setting);
            List<HudWidget.HudOption> options = safeOptions(setting);
            for (int index = 0; index < options.size(); index++) {
                if (settingsDropdownOptionBounds(list, index).contains(mouseX, mouseY)) {
                    setting.setString().accept(options.get(index).value());
                    openDropdownKey = null;
                    WidgetConfigManager.updateWidget(settingsWidget);
                    return true;
                }
            }

            openDropdownKey = null;
            return false;
        }

        openDropdownKey = null;
        return false;
    }

    private boolean handleSettingsClick(double mouseX, double mouseY, int button) {
        int x = modalX();
        int y = modalY();
        int modalWidth = modalWidth();

        int closeX = x + modalWidth - 18;
        int closeY = y + 6;
        if (inside(mouseX, mouseY, closeX, closeY, 10, 10)) {
            closeSettingsModal();
            return true;
        }

        if (handleOpenSettingsDropdownClick(mouseX, mouseY)) {
            return true;
        }

        if (!settingsViewportBounds().contains(mouseX, mouseY)) {
            openDropdownKey = null;
            return true;
        }

        SettingsLayout layout = beginSettingsLayout();
        for (HudWidget.HudSetting setting : safeSettings(settingsWidget)) {
            int rowY = nextSettingY(layout, setting);

            if (setting.type() == HudWidget.HudSetting.Type.SECTION) {
                continue;
            }

            if (inside(mouseX, mouseY, layout.resetX, rowY - 1, RESET_W, RESET_H)) {
                WidgetConfigManager.clearSetting(settingsWidget.getName(), setting.key(), true);

                switch (setting.type()) {
                    case TOGGLE -> setting.setBool().accept(setting.getBool().getAsBoolean());
                    case COLOR -> setting.setColor().accept(setting.getColor().getAsInt());
                    case SLIDER -> setting.setFloat().accept((float) setting.getFloat().getAsDouble());
                    case DROPDOWN -> setting.setString().accept(setting.getString().get());
                    case CUSTOM_LIST -> {
                        if (setting.customList() != null) {
                            setting.customList().setEntries().accept(List.of());
                        }
                    }
                    case SECTION -> {
                    }
                }

                if (setting.key().equals(openColorKey)) {
                    openColorKey = null;
                    colorPicker = null;
                }
                if (setting.key().equals(openDropdownKey)) {
                    openDropdownKey = null;
                }
                if (setting.key().equals(draggingSliderKey)) {
                    draggingSliderKey = null;
                }

                WidgetConfigManager.updateWidget(settingsWidget);
                updateSettingsScrollBounds();
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
                        if (!newValue) {
                            openDropdownKey = null;
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
                        int px = modalX() + modalWidth() + gap;
                        int py = modalY();
                        if (px + pickerW > this.width) {
                            px = modalX() - pickerW - gap;
                        }
                        openDropdownKey = null;
                        openColorPicker(setting, px, py, pickerW);
                        return true;
                    }
                }
                case SLIDER -> {
                    if (!setting.enabled().getAsBoolean()) {
                        continue;
                    }

                    if (inside(mouseX, mouseY, layout.sliderBarX, rowY - 1, layout.sliderBarW, 12)) {
                        openDropdownKey = null;
                        draggingSliderKey = setting.key();
                        float nextValue = sliderValueForMouse(setting, layout.sliderBarX, layout.sliderBarW, mouseX);
                        setting.setFloat().accept(nextValue);
                        WidgetConfigManager.updateWidget(settingsWidget);
                        return true;
                    }
                }
                case DROPDOWN -> {
                    if (!setting.enabled().getAsBoolean()) {
                        continue;
                    }

                    if (settingsDropdownButtonBounds(layout, rowY).contains(mouseX, mouseY)) {
                        if (safeOptions(setting).isEmpty()) {
                            openDropdownKey = null;
                            return true;
                        }

                        colorPicker = null;
                        openColorKey = null;
                        openDropdownKey = setting.key().equals(openDropdownKey) ? null : setting.key();
                        return true;
                    }
                }
                case CUSTOM_LIST -> {
                    if (!setting.enabled().getAsBoolean() || setting.customList() == null) {
                        continue;
                    }

                    if (inside(mouseX, mouseY, customAddX(layout), rowY - 2, CUSTOM_ADD_W, layout.btnH)) {
                        if (client != null) {
                            client.setScreen(new CustomListFormScreen(this, setting));
                        }
                        return true;
                    }

                    List<HudWidget.CustomEntry> entries = setting.customList().getEntries().get();
                    for (int index = 0; index < entries.size(); index++) {
                        Rect card = customEntryBounds(layout, rowY, index);
                        Rect remove = customRemoveBounds(card);
                        if (remove.contains(mouseX, mouseY)) {
                            List<HudWidget.CustomEntry> updated = new ArrayList<>(entries);
                            updated.remove(index);
                            setting.customList().setEntries().accept(List.copyOf(updated));
                            WidgetConfigManager.updateWidget(settingsWidget);
                            updateSettingsScrollBounds();
                            return true;
                        }

                        if (card.contains(mouseX, mouseY) && client != null) {
                            client.setScreen(new CustomListFormScreen(this, setting, index, entries.get(index)));
                            return true;
                        }
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
        int modalWidth = modalWidth();
        int modalHeight = modalHeight();
        updateSettingsScrollBounds();

        ctx.fill(0, 0, this.width, this.height, MODAL_OVERLAY_COLOR);
        if (useMinecraftTheme()) {
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, VANILLA_POPUP_BACKGROUND_SPRITE, x, y, modalWidth, modalHeight);
        } else {
            ctx.fill(x, y, x + modalWidth, y + modalHeight, MODAL_PANEL_COLOR);
            drawBorder(ctx, x, y, modalWidth, modalHeight, MODAL_BORDER_COLOR);
        }

        ctx.drawText(textRenderer, settingsWidget.getName() + " Settings", x + 8, y + 8, MODAL_TEXT_COLOR, false);
        ctx.drawText(textRenderer, "x", x + modalWidth - 17, y + 6, MODAL_TEXT_COLOR, false);

        SettingsLayout layout = beginSettingsLayout();
        boolean grouped = false;
        HudWidget.HudSetting openDropdownSetting = null;
        int openDropdownRowY = 0;
        Rect viewport = settingsViewportBounds();
        ctx.enableScissor(viewport.x, viewport.y, viewport.right(), viewport.bottom());

        for (HudWidget.HudSetting setting : safeSettings(settingsWidget)) {
            int rowY = nextSettingY(layout, setting);

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
                    ctx.drawText(textRenderer, setting.label(), labelX, rowY, enabled ? MODAL_TEXT_COLOR : MODAL_DISABLED_TEXT_COLOR, false);
                    drawTogglePill(ctx, layout.toggleX, rowY - 2, layout.toggleW, layout.btnH, value, enabled);
                }
                case COLOR -> {
                    boolean enabled = setting.enabled().getAsBoolean();
                    int color = setting.getColor().getAsInt();
                    ctx.drawText(textRenderer, setting.label(), labelX, rowY, enabled ? MODAL_TEXT_COLOR : MODAL_DISABLED_TEXT_COLOR, false);

                    if (enabled) {
                        drawPickButton(ctx, layout.btnX, rowY - 2, layout.btnW, layout.btnH, mouseX, mouseY);
                        drawSwatch(ctx, layout.btnX - 18, rowY - 1, color);
                    } else {
                        ctx.drawText(textRenderer, "-", layout.btnX + 26, rowY, MODAL_DISABLED_TEXT_COLOR, false);
                    }
                }
                case SLIDER -> {
                    boolean enabled = setting.enabled().getAsBoolean();
                    float value = (float) setting.getFloat().getAsDouble();
                    ctx.drawText(textRenderer, setting.label(), labelX, rowY, enabled ? MODAL_TEXT_COLOR : MODAL_DISABLED_TEXT_COLOR, false);
                    drawSliderRow(ctx, layout, rowY, value, setting.min(), setting.max(), setting.step(), enabled);
                }
                case DROPDOWN -> {
                    boolean enabled = setting.enabled().getAsBoolean();
                    String value = currentOptionLabel(setting);
                    ctx.drawText(textRenderer, setting.label(), labelX, rowY, enabled ? MODAL_TEXT_COLOR : MODAL_DISABLED_TEXT_COLOR, false);

                    if (enabled) {
                        boolean open = setting.key().equals(openDropdownKey);
                        drawDropdownButton(ctx, settingsDropdownButtonBounds(layout, rowY), value, mouseX, mouseY, open);
                        if (open) {
                            openDropdownSetting = setting;
                            openDropdownRowY = rowY;
                        }
                    } else {
                        ctx.drawText(textRenderer, "-", layout.btnX + 26, rowY, MODAL_DISABLED_TEXT_COLOR, false);
                    }
                }
                case CUSTOM_LIST -> {
                    boolean enabled = setting.enabled().getAsBoolean() && setting.customList() != null;
                    ctx.drawText(textRenderer, setting.label(), labelX, rowY, enabled ? MODAL_TEXT_COLOR : MODAL_DISABLED_TEXT_COLOR, false);

                    if (!enabled) {
                        ctx.drawText(textRenderer, "-", customAddX(layout) + 18, rowY, MODAL_DISABLED_TEXT_COLOR, false);
                        continue;
                    }

                    drawCustomAddButton(ctx, customAddX(layout), rowY - 2, mouseX, mouseY);
                    HudWidget.CustomListSpec spec = setting.customList();
                    List<HudWidget.CustomEntry> entries = spec.getEntries().get();
                    for (int index = 0; index < entries.size(); index++) {
                        Rect card = customEntryBounds(layout, rowY, index);
                        HudWidget.CustomEntry entry = entries.get(index);
                        String title = safeCustomText(spec.title(), entry);
                        String description = safeCustomText(spec.description(), entry);
                        Rect remove = customRemoveBounds(card);
                        boolean hovered = card.contains(mouseX, mouseY);

                        ctx.fill(
                                card.x,
                                card.y,
                                card.right(),
                                card.bottom(),
                                hovered ? MODAL_CONTROL_HOVER_COLOR : MODAL_CONTROL_FILL_COLOR
                        );
                        drawBorder(ctx, card.x, card.y, card.width, card.height, MODAL_SECTION_DIVIDER_COLOR);
                        ctx.drawText(
                                textRenderer,
                                trimToWidth(title, Math.max(1, remove.x - card.x - 8)),
                                card.x + 4,
                                card.y + 4,
                                MODAL_TEXT_COLOR,
                                false
                        );
                        ctx.drawText(
                                textRenderer,
                                trimToWidth(description, Math.max(1, remove.x - card.x - 8)),
                                card.x + 4,
                                card.y + 15,
                                MODAL_VALUE_TEXT_COLOR,
                                false
                        );
                        ctx.drawText(textRenderer, "x", remove.x + 3, remove.y + 2, MODAL_TEXT_COLOR, false);
                    }
                }
                case SECTION -> {
                }
            }
        }
        renderOpenSettingsDropdown(ctx, layout, openDropdownSetting, openDropdownRowY, mouseX, mouseY);
        ctx.disableScissor();
        drawSettingsScrollbar(ctx, viewport);

        if (colorPicker != null) {
            int gap = 6;
            int pickerW = colorPicker.getWidth();
            int px = modalX() + modalWidth() + gap;
            int py = modalY();
            if (px + pickerW > this.width) {
                px = modalX() - pickerW - gap;
            }
            colorPicker.setPos(px, py);
            colorPicker.render(ctx, mouseX, mouseY);
        }
    }

    private String currentOptionLabel(HudWidget.HudSetting setting) {
        String current = setting.getString().get();
        for (HudWidget.HudOption option : safeOptions(setting)) {
            if (Objects.equals(option.value(), current)) {
                return optionText(option);
            }
        }
        return current == null || current.isBlank() ? "-" : current;
    }

    private List<HudWidget.HudOption> safeOptions(HudWidget.HudSetting setting) {
        List<HudWidget.HudOption> options = setting.options();
        return options == null ? List.of() : options;
    }

    private String optionText(HudWidget.HudOption option) {
        if (option.label() != null && !option.label().isBlank()) {
            return option.label();
        }
        return option.value() == null ? "" : option.value();
    }

    private Rect settingsDropdownButtonBounds(SettingsLayout layout, int rowY) {
        return new Rect(layout.btnX, rowY - 2, layout.btnW, layout.btnH);
    }

    private Rect settingsDropdownListBounds(SettingsLayout layout, int rowY, HudWidget.HudSetting setting) {
        Rect button = settingsDropdownButtonBounds(layout, rowY);
        int listHeight = Math.max(1, safeOptions(setting).size() * SETTINGS_DROPDOWN_OPTION_H);
        int belowY = button.bottom() + SETTINGS_DROPDOWN_GAP;
        int aboveY = button.y - SETTINGS_DROPDOWN_GAP - listHeight;
        Rect viewport = settingsViewportBounds();

        int y = belowY;
        if (belowY + listHeight > viewport.bottom() && aboveY >= viewport.y) {
            y = aboveY;
        } else if (belowY + listHeight > viewport.bottom()) {
            y = Math.max(viewport.y, viewport.bottom() - listHeight);
        }

        return new Rect(button.x, y, button.width, listHeight);
    }

    private Rect settingsDropdownOptionBounds(Rect list, int index) {
        int optionY = list.y + index * SETTINGS_DROPDOWN_OPTION_H + 1;
        return new Rect(list.x + 1, optionY, Math.max(1, list.width - 2), SETTINGS_DROPDOWN_OPTION_H - 1);
    }

    private void drawDropdownButton(DrawContext ctx, Rect rect, String label, int mouseX, int mouseY, boolean open) {
        boolean hovered = rect.contains(mouseX, mouseY);
        String visibleLabel = trimToWidth(label, Math.max(1, rect.width - 14));
        if (useMinecraftTheme()) {
            drawMinecraftButton(ctx, rect, hovered || open, false);
            ctx.drawText(
                    textRenderer,
                    visibleLabel,
                    rect.x + 5,
                    rect.y + (rect.height - textRenderer.fontHeight) / 2 + 1,
                    minecraftTextColor(hovered || open, false),
                    false
            );
            ctx.drawText(
                    textRenderer,
                    open ? "▲" : "▼",
                    rect.right() - 8,
                    rect.y + (rect.height - textRenderer.fontHeight) / 2 + 1,
                    minecraftTextColor(hovered || open, false),
                    false
            );
            return;
        }

        int bg = hovered || open ? MODAL_CONTROL_HOVER_COLOR : MODAL_CONTROL_FILL_COLOR;
        ctx.fill(rect.x, rect.y, rect.right(), rect.bottom(), bg);
        drawBorder(ctx, rect.x, rect.y, rect.width, rect.height, UI_BORDER_DARK);
        ctx.drawText(
                textRenderer,
                visibleLabel,
                rect.x + 4,
                rect.y + 3,
                MODAL_TEXT_COLOR,
                false
        );
        ctx.drawText(
                textRenderer,
                open ? "^" : "v",
                rect.right() - 8,
                rect.y + 3,
                MODAL_TEXT_COLOR,
                false
        );
    }

    private void renderOpenSettingsDropdown(DrawContext ctx, SettingsLayout layout,
                                            HudWidget.HudSetting setting, int rowY,
                                            int mouseX, int mouseY) {
        if (setting == null || !setting.enabled().getAsBoolean() || safeOptions(setting).isEmpty()) {
            return;
        }

        Rect list = settingsDropdownListBounds(layout, rowY, setting);
        if (useMinecraftTheme()) {
            drawPanel(ctx, list, UI_PANEL_COLOR, UI_PANEL_COLOR, UI_BORDER_COLOR);
        } else {
            ctx.fill(list.x, list.y, list.right(), list.bottom(), MODAL_PANEL_COLOR);
            drawBorder(ctx, list.x, list.y, list.width, list.height, UI_BORDER_COLOR);
        }

        String current = setting.getString().get();
        List<HudWidget.HudOption> options = safeOptions(setting);
        for (int index = 0; index < options.size(); index++) {
            HudWidget.HudOption option = options.get(index);
            Rect optionBounds = settingsDropdownOptionBounds(list, index);
            boolean hovered = optionBounds.contains(mouseX, mouseY);
            boolean selected = Objects.equals(option.value(), current);
            String label = trimToWidth(optionText(option), Math.max(1, optionBounds.width - 10));

            if (useMinecraftTheme()) {
                int color = selected ? 0xFFFFFFA0 : minecraftTextColor(hovered, false);
                ctx.drawCenteredTextWithShadow(
                        textRenderer,
                        Text.literal(label),
                        optionBounds.x + optionBounds.width / 2,
                        optionBounds.y + (optionBounds.height - textRenderer.fontHeight) / 2,
                        color
                );
                continue;
            }

            if (hovered || selected) {
                ctx.fill(
                        optionBounds.x,
                        optionBounds.y,
                        optionBounds.right(),
                        optionBounds.bottom(),
                        hovered ? MODAL_CONTROL_HOVER_COLOR : UI_PANEL_HOVER_COLOR
                );
            }

            if (index > 0) {
                ctx.drawHorizontalLine(optionBounds.x + 3, optionBounds.right() - 3, optionBounds.y, DIVIDER_COLOR);
            }

            ctx.drawText(
                    textRenderer,
                    label,
                    optionBounds.x + 5,
                    optionBounds.y + 4,
                    selected ? MODAL_TEXT_COLOR : MODAL_VALUE_TEXT_COLOR,
                    false
            );
        }
    }

    private void drawPickButton(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        if (useMinecraftTheme()) {
            Rect rect = new Rect(x, y, w, h);
            drawMinecraftButton(ctx, rect, hovered, false);
            drawMinecraftCenteredLabel(ctx, rect, "Pick", hovered);
            return;
        }

        int bg = hovered ? MODAL_CONTROL_HOVER_COLOR : MODAL_CONTROL_FILL_COLOR;
        ctx.fill(x, y, x + w, y + h, bg);
        drawBorder(ctx, x, y, w, h, UI_BORDER_DARK);
        ctx.drawText(textRenderer, "Pick", x + 18, y + 3, MODAL_TEXT_COLOR, false);
    }

    private void drawCustomAddButton(DrawContext ctx, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, CUSTOM_ADD_W, 14);
        if (useMinecraftTheme()) {
            Rect rect = new Rect(x, y, CUSTOM_ADD_W, 14);
            drawMinecraftButton(ctx, rect, hovered, false);
            drawMinecraftCenteredLabel(ctx, rect, "Add", hovered);
            return;
        }

        ctx.fill(x, y, x + CUSTOM_ADD_W, y + 14, hovered ? MODAL_CONTROL_HOVER_COLOR : MODAL_CONTROL_FILL_COLOR);
        drawBorder(ctx, x, y, CUSTOM_ADD_W, 14, UI_BORDER_DARK);
        ctx.drawText(textRenderer, "Add", x + 12, y + 3, MODAL_TEXT_COLOR, false);
    }

    private void drawSettingsScrollbar(DrawContext ctx, Rect viewport) {
        if (settingsMaxScroll <= 0) {
            return;
        }

        int trackX = viewport.right() - 3;
        int trackHeight = viewport.height;
        int contentHeight = settingsContentHeight();
        int thumbHeight = Math.max(12, trackHeight * trackHeight / Math.max(1, contentHeight));
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = viewport.y + Math.round(travel * (settingsScroll / (float) settingsMaxScroll));

        ctx.fill(trackX, viewport.y, trackX + 2, viewport.bottom(), SCROLL_TRACK_COLOR);
        ctx.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, SCROLL_THUMB_COLOR);
    }

    private void drawSwatch(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x, y, x + 12, y + 12, color);
        drawBorder(ctx, x, y, 12, 12, UI_BORDER_DARK);
    }

    private void drawTogglePill(DrawContext ctx, int x, int y, int w, int h, boolean on, boolean enabled) {
        if (useMinecraftTheme()) {
            Rect rect = new Rect(x, y, w, h);
            drawMinecraftButton(ctx, rect, on, !enabled);
            drawMinecraftCenteredLabel(ctx, rect, on ? "ON" : "OFF", on);
            return;
        }

        int bg = on ? MODAL_TOGGLE_ON_COLOR : MODAL_TOGGLE_OFF_COLOR;
        if (!enabled) {
            bg = MODAL_DISABLED_FILL_COLOR;
        }

        ctx.fill(x, y, x + w, y + h, bg);
        drawBorder(ctx, x, y, w, h, UI_BORDER_DARK);

        int textColor = enabled ? UI_BORDER_DARK : shadeColor(UI_BORDER_DARK, 0.65f);
        ctx.drawText(textRenderer, on ? "ON" : "OFF", x + 10, y + 3, textColor, false);
    }

    private void drawResetButton(DrawContext ctx, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, RESET_W, RESET_H);
        if (useMinecraftTheme()) {
            Rect rect = new Rect(x, y, RESET_W, RESET_H);
            drawMinecraftButton(ctx, rect, hovered, false);
            drawMinecraftCenteredLabel(ctx, rect, "R", hovered);
            return;
        }

        int bg = hovered ? MODAL_CONTROL_HOVER_COLOR : MODAL_CONTROL_FILL_COLOR;

        ctx.fill(x, y, x + RESET_W, y + RESET_H, bg);
        drawBorder(ctx, x, y, RESET_W, RESET_H, UI_BORDER_DARK);
        ctx.drawText(textRenderer, "R", x + 3, y + 2, MODAL_TEXT_COLOR, false);
    }

    private void drawSectionRow(DrawContext ctx, SettingsLayout layout, int rowY, String label) {
        ctx.drawText(textRenderer, label, layout.startX, rowY, MODAL_SECTION_COLOR, false);

        int dividerX = layout.startX + textRenderer.getWidth(label) + 6;
        int dividerY = rowY + (textRenderer.fontHeight / 2);
        if (dividerX < layout.resetX - 4) {
            ctx.drawHorizontalLine(dividerX, layout.resetX - 4, dividerY, MODAL_SECTION_DIVIDER_COLOR);
        }
    }

    private void drawSliderRow(DrawContext ctx, SettingsLayout layout, int rowY,
                               float value, float min, float max, float step, boolean enabled) {
        int barX = layout.sliderBarX;
        int barY = rowY - 1;
        int barW = layout.sliderBarW;
        int barH = 12;

        if (useMinecraftTheme()) {
            Rect field = new Rect(barX, barY, barW, barH);
            drawMinecraftTextField(ctx, field, enabled);

            float t = (max == min) ? 0f : (value - min) / (max - min);
            t = Math.max(0f, Math.min(1f, t));
            int knobWidth = 8;
            int knobX = barX + (int) (t * (barW - knobWidth));
            drawMinecraftButton(ctx, new Rect(knobX, barY - 2, knobWidth, barH + 4), enabled, !enabled);

            String displayValue = (step >= 1f) ? String.format("%.0f", value) : String.format("%.2f", value);
            ctx.drawText(
                    textRenderer,
                    displayValue,
                    barX + (barW / 2) - (textRenderer.getWidth(displayValue) / 2),
                    rowY + 1,
                    enabled ? 0xFFE0E0E0 : 0xFFA0A0A0,
                    false
            );
            return;
        }

        int bg = enabled ? SLIDER_FILL_COLOR : SLIDER_DISABLED_FILL_COLOR;
        ctx.fill(barX, barY, barX + barW, barY + barH, bg);
        drawBorder(ctx, barX, barY, barW, barH, UI_BORDER_DARK);

        float t = (max == min) ? 0f : (value - min) / (max - min);
        t = Math.max(0f, Math.min(1f, t));

        int knobX = barX + (int) (t * (barW - 4));
        ctx.fill(knobX, barY, knobX + 4, barY + barH, enabled ? SLIDER_KNOB_COLOR : SLIDER_KNOB_DISABLED_COLOR);

        String displayValue = (step >= 1f) ? String.format("%.0f", value) : String.format("%.2f", value);
        ctx.drawText(textRenderer, displayValue,
                barX + (barW / 2) - (textRenderer.getWidth(displayValue) / 2),
                rowY + 1,
                enabled ? MODAL_VALUE_TEXT_COLOR : MODAL_DISABLED_TEXT_COLOR,
                false
        );
    }

    private boolean useMinecraftTheme() {
        return activeTheme == EditorTheme.MINECRAFT_DEFAULT;
    }

    private void renderMinecraftMenuBackground(DrawContext ctx) {
        int tile = 32;
        for (int x = 0; x < this.width; x += tile) {
            for (int y = 0; y < this.height; y += tile) {
                int w = Math.min(tile, this.width - x);
                int h = Math.min(tile, this.height - y);
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, VANILLA_MENU_BACKGROUND_TEXTURE, x, y, 0, 0, w, h, tile, tile);
            }
        }
        ctx.fill(0, 0, this.width, this.height, viewMode == ViewMode.LAYOUT ? 0x22000000 : 0x44000000);
    }

    private void drawMinecraftButton(DrawContext ctx, Rect rect, boolean hovered, boolean disabled) {
        Identifier sprite = disabled ? VANILLA_BUTTON_DISABLED_SPRITE : (hovered ? VANILLA_BUTTON_HOVERED_SPRITE : VANILLA_BUTTON_SPRITE);
        ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, rect.x, rect.y, rect.width, rect.height);
    }

    private void drawMinecraftTextField(DrawContext ctx, Rect rect, boolean focused) {
        ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, focused ? VANILLA_TEXT_FIELD_HIGHLIGHTED_SPRITE : VANILLA_TEXT_FIELD_SPRITE, rect.x, rect.y, rect.width, rect.height);
    }

    private void drawMinecraftCenteredLabel(DrawContext ctx, Rect rect, String label, boolean highlighted) {

        if (useMinecraftTheme()) {
            ctx.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal(label),
                    rect.x + rect.width / 2,
                    rect.y + (rect.height - textRenderer.fontHeight) / 2,
                    minecraftTextColor(highlighted, false)
            );
        } else {
            ctx.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal(label),
                    rect.x + rect.width / 2 + 1,
                    rect.y + (rect.height - textRenderer.fontHeight) / 2 + 1,
                    minecraftTextColor(highlighted, false)
            );
        }

    }

    private int minecraftTextColor(boolean highlighted, boolean disabled) {
        if (disabled) {
            return 0xFFA0A0A0;
        }
        return highlighted ? 0xFFFFFFA0 : 0xFFE0E0E0;
    }

    private void renderThemeDropdown(DrawContext ctx, int mouseX, int mouseY) {
        Rect listBounds = themeDropdownListBounds();
        if (useMinecraftTheme()) {
//            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, VANILLA_POPUP_BACKGROUND_SPRITE, listBounds.x, listBounds.y, listBounds.width, listBounds.height + 15);
            drawPanel(ctx, listBounds, UI_PANEL_COLOR, UI_PANEL_COLOR, UI_BORDER_COLOR);
        } else {
            ctx.fill(listBounds.x, listBounds.y, listBounds.right(), listBounds.bottom(), UI_PANEL_COLOR);
            drawBorder(ctx, listBounds.x, listBounds.y, listBounds.width, listBounds.height, UI_BORDER_COLOR);
        }

        EditorTheme[] themes = EditorTheme.values();
        for (int i = 0; i < themes.length; i++) {
            Rect option = themeOptionBounds(i);
            EditorTheme theme = themes[i];
            boolean hovered = option.contains(mouseX, mouseY);
            boolean selected = theme == activeTheme;

            if (useMinecraftTheme()) {
                int color = selected ? 0xFFFFFFA0 : minecraftTextColor(hovered, false);
                ctx.drawCenteredTextWithShadow(
                        textRenderer,
                        Text.literal(trimToWidth(theme.label, option.width - 10)),
                        option.x + option.width / 2,
                        option.y + (option.height - textRenderer.fontHeight) / 2,
                        color
                );
            } else {
                if (hovered || selected) {
                    ctx.fill(option.x, option.y, option.right(), option.bottom(), hovered ? BUTTON_FILL_HOVER_COLOR : UI_PANEL_HOVER_COLOR);
                }

                if (i > 0) {
                    ctx.drawHorizontalLine(option.x + 4, option.right() - 4, option.y, DIVIDER_COLOR);
                }

                ctx.drawText(
                        textRenderer,
                        trimToWidth(theme.label, option.width - 12),
                        option.x + 6,
                        option.y + 5,
                        selected ? UI_BUTTON_TEXT_COLOR : UI_SUBTEXT_COLOR,
                        false
                );
            }
        }
    }

    private void applyTheme(EditorTheme theme) {
        activeTheme = theme == null ? EditorTheme.DEFAULT : theme;

        switch (activeTheme) {
            case DEFAULT -> {
                UI_PANEL_COLOR = 0xFF111111;
                UI_PANEL_HOVER_COLOR = 0xFF1A1A1A;
                UI_BORDER_COLOR = 0xFFFFFFFF;
                UI_BORDER_DARK = 0xFF000000;
                UI_BUTTON_TEXT_COLOR = 0xFFFFFFFF;
                UI_SUBTEXT_COLOR = 0xFFAAAAAA;
                MOD_TILE_ENABLED_COLOR = 0xFF26C665;
                MOD_TILE_ENABLED_FOOTER = 0xFF1D8E49;
                MOD_TILE_DISABLED_COLOR = 0xFF111111;
                MOD_TILE_DISABLED_FOOTER = 0xFF0A0A0A;
                MOD_TILE_DISABLED_BORDER = 0xFF000000;
                BUTTON_FILL_COLOR = 0xFF333333;
                BUTTON_FILL_HOVER_COLOR = 0xFF444444;
                BUTTON_BORDER_COLOR = 0xFFFFFFFF;
                BUTTON_BORDER_HOVER_COLOR = 0xFFFFFFFF;
                BACKDROP_TOP_COLOR = 0x88000000;
                BACKDROP_BOTTOM_COLOR = 0xAA000000;
                LAYOUT_BACKDROP_TOP_COLOR = 0x44000000;
                LAYOUT_BACKDROP_BOTTOM_COLOR = 0x66000000;
                DIVIDER_COLOR = 0x66000000;
                SCROLL_TRACK_COLOR = 0x66000000;
                SCROLL_THUMB_COLOR = 0xCCFFFFFF;
                MODAL_OVERLAY_COLOR = 0x88000000;
                MODAL_PANEL_COLOR = 0xFF111111;
                MODAL_BORDER_COLOR = 0xFFFFFFFF;
                MODAL_TEXT_COLOR = 0xFFFFFFFF;
                MODAL_DISABLED_TEXT_COLOR = 0xFF777777;
                MODAL_SECTION_COLOR = 0xFF8EC5FF;
                MODAL_SECTION_DIVIDER_COLOR = 0xFF2D2D2D;
                MODAL_CONTROL_FILL_COLOR = 0xFF333333;
                MODAL_CONTROL_HOVER_COLOR = 0xFF444444;
                MODAL_TOGGLE_ON_COLOR = 0xFF2ECC71;
                MODAL_TOGGLE_OFF_COLOR = 0xFF7F8C8D;
                MODAL_DISABLED_FILL_COLOR = 0xFF444444;
                MODAL_VALUE_TEXT_COLOR = 0xFFAAAAAA;
                SLIDER_FILL_COLOR = 0xFF2A2A2A;
                SLIDER_DISABLED_FILL_COLOR = 0xFF1F1F1F;
                SLIDER_KNOB_COLOR = 0xFF7F8C8D;
                SLIDER_KNOB_DISABLED_COLOR = 0xFF444444;
            }
            case MIDNIGHT -> {
                UI_PANEL_COLOR = 0xFF0B1020;
                UI_PANEL_HOVER_COLOR = 0xFF131A2E;
                UI_BORDER_COLOR = 0xFFD7E3FF;
                UI_BORDER_DARK = 0xFF02050C;
                UI_BUTTON_TEXT_COLOR = 0xFFF4F7FF;
                UI_SUBTEXT_COLOR = 0xFF97A6C4;
                MOD_TILE_ENABLED_COLOR = 0xFF2A4F9F;
                MOD_TILE_ENABLED_FOOTER = 0xFF1D3870;
                MOD_TILE_DISABLED_COLOR = 0xFF0B1020;
                MOD_TILE_DISABLED_FOOTER = 0xFF070A15;
                MOD_TILE_DISABLED_BORDER = 0xFF02050C;
                BUTTON_FILL_COLOR = 0xFF17233A;
                BUTTON_FILL_HOVER_COLOR = 0xFF22324F;
                BUTTON_BORDER_COLOR = 0xFFD7E3FF;
                BUTTON_BORDER_HOVER_COLOR = 0xFFF4F7FF;
                BACKDROP_TOP_COLOR = 0x88070A15;
                BACKDROP_BOTTOM_COLOR = 0xBB02050C;
                LAYOUT_BACKDROP_TOP_COLOR = 0x44070A15;
                LAYOUT_BACKDROP_BOTTOM_COLOR = 0x66020B17;
                DIVIDER_COLOR = 0x66263A62;
                SCROLL_TRACK_COLOR = 0x6613223B;
                SCROLL_THUMB_COLOR = 0xCCD7E3FF;
                MODAL_OVERLAY_COLOR = 0x90050A14;
                MODAL_PANEL_COLOR = 0xFF0B1020;
                MODAL_BORDER_COLOR = 0xFFD7E3FF;
                MODAL_TEXT_COLOR = 0xFFD7E3FF;
                MODAL_DISABLED_TEXT_COLOR = 0xFF5E6B87;
                MODAL_SECTION_COLOR = 0xFF7AA2FF;
                MODAL_SECTION_DIVIDER_COLOR = 0xFF23314F;
                MODAL_CONTROL_FILL_COLOR = 0xFF17233A;
                MODAL_CONTROL_HOVER_COLOR = 0xFF22324F;
                MODAL_TOGGLE_ON_COLOR = 0xFF3C6EDE;
                MODAL_TOGGLE_OFF_COLOR = 0xFF4F5F7A;
                MODAL_DISABLED_FILL_COLOR = 0xFF22324F;
                MODAL_VALUE_TEXT_COLOR = 0xFFB4C4E7;
                SLIDER_FILL_COLOR = 0xFF142038;
                SLIDER_DISABLED_FILL_COLOR = 0xFF0E1626;
                SLIDER_KNOB_COLOR = 0xFF6B91FF;
                SLIDER_KNOB_DISABLED_COLOR = 0xFF22324F;
            }
            case DUSK -> {
                UI_PANEL_COLOR = 0xFF181218;
                UI_PANEL_HOVER_COLOR = 0xFF241A24;
                UI_BORDER_COLOR = 0xFFF2D7CB;
                UI_BORDER_DARK = 0xFF0B070B;
                UI_BUTTON_TEXT_COLOR = 0xFFF8EEE9;
                UI_SUBTEXT_COLOR = 0xFFBCA59D;
                MOD_TILE_ENABLED_COLOR = 0xFF8B4E38;
                MOD_TILE_ENABLED_FOOTER = 0xFF67392B;
                MOD_TILE_DISABLED_COLOR = 0xFF181218;
                MOD_TILE_DISABLED_FOOTER = 0xFF110D11;
                MOD_TILE_DISABLED_BORDER = 0xFF0B070B;
                BUTTON_FILL_COLOR = 0xFF352633;
                BUTTON_FILL_HOVER_COLOR = 0xFF483343;
                BUTTON_BORDER_COLOR = 0xFFF2D7CB;
                BUTTON_BORDER_HOVER_COLOR = 0xFFF8EEE9;
                BACKDROP_TOP_COLOR = 0x88130D13;
                BACKDROP_BOTTOM_COLOR = 0xBB090609;
                LAYOUT_BACKDROP_TOP_COLOR = 0x44130D13;
                LAYOUT_BACKDROP_BOTTOM_COLOR = 0x660B070B;
                DIVIDER_COLOR = 0x66533A34;
                SCROLL_TRACK_COLOR = 0x66241424;
                SCROLL_THUMB_COLOR = 0xCCF2D7CB;
                MODAL_OVERLAY_COLOR = 0x900D070D;
                MODAL_PANEL_COLOR = 0xFF181218;
                MODAL_BORDER_COLOR = 0xFFF2D7CB;
                MODAL_TEXT_COLOR = 0xFFF2D7CB;
                MODAL_DISABLED_TEXT_COLOR = 0xFF5D4842;
                MODAL_SECTION_COLOR = 0xFFE29A6A;
                MODAL_SECTION_DIVIDER_COLOR = 0xFF47312E;
                MODAL_CONTROL_FILL_COLOR = 0xFF352633;
                MODAL_CONTROL_HOVER_COLOR = 0xFF483343;
                MODAL_TOGGLE_ON_COLOR = 0xFFB56A4A;
                MODAL_TOGGLE_OFF_COLOR = 0xFF77615B;
                MODAL_DISABLED_FILL_COLOR = 0xFF483343;
                MODAL_VALUE_TEXT_COLOR = 0xFFD8C0B8;
                SLIDER_FILL_COLOR = 0xFF2C1F29;
                SLIDER_DISABLED_FILL_COLOR = 0xFF1E161C;
                SLIDER_KNOB_COLOR = 0xFFD08A63;
                SLIDER_KNOB_DISABLED_COLOR = 0xFF483343;
            }
            case RED -> {
                UI_PANEL_COLOR = 0xFF170D0E;
                UI_PANEL_HOVER_COLOR = 0xFF231416;
                UI_BORDER_COLOR = 0xFFF1D8DA;
                UI_BORDER_DARK = 0xFF050202;
                UI_BUTTON_TEXT_COLOR = 0xFFF8EFF0;
                UI_SUBTEXT_COLOR = 0xFFC0A2A6;
                MOD_TILE_ENABLED_COLOR = 0xFF8B3139;
                MOD_TILE_ENABLED_FOOTER = 0xFF67242A;
                MOD_TILE_DISABLED_COLOR = 0xFF170D0E;
                MOD_TILE_DISABLED_FOOTER = 0xFF100809;
                MOD_TILE_DISABLED_BORDER = 0xFF050202;
                BUTTON_FILL_COLOR = 0xFF341C20;
                BUTTON_FILL_HOVER_COLOR = 0xFF47252A;
                BUTTON_BORDER_COLOR = 0xFFF1D8DA;
                BUTTON_BORDER_HOVER_COLOR = 0xFFF8EFF0;
                BACKDROP_TOP_COLOR = 0x88110809;
                BACKDROP_BOTTOM_COLOR = 0xBB050202;
                LAYOUT_BACKDROP_TOP_COLOR = 0x44110809;
                LAYOUT_BACKDROP_BOTTOM_COLOR = 0x66090506;
                DIVIDER_COLOR = 0x66472A2D;
                SCROLL_TRACK_COLOR = 0x66170D10;
                SCROLL_THUMB_COLOR = 0xCCF1D8DA;
                MODAL_OVERLAY_COLOR = 0x90090203;
                MODAL_PANEL_COLOR = 0xFF170D0E;
                MODAL_BORDER_COLOR = 0xFFF1D8DA;
                MODAL_TEXT_COLOR = 0xFFF1D8DA;
                MODAL_DISABLED_TEXT_COLOR = 0xFF5B4347;
                MODAL_SECTION_COLOR = 0xFFD76A73;
                MODAL_SECTION_DIVIDER_COLOR = 0xFF43282C;
                MODAL_CONTROL_FILL_COLOR = 0xFF341C20;
                MODAL_CONTROL_HOVER_COLOR = 0xFF47252A;
                MODAL_TOGGLE_ON_COLOR = 0xFFB93C46;
                MODAL_TOGGLE_OFF_COLOR = 0xFF7B6063;
                MODAL_DISABLED_FILL_COLOR = 0xFF47252A;
                MODAL_VALUE_TEXT_COLOR = 0xFFD7B9BC;
                SLIDER_FILL_COLOR = 0xFF2A171A;
                SLIDER_DISABLED_FILL_COLOR = 0xFF1C1011;
                SLIDER_KNOB_COLOR = 0xFFDB6B74;
                SLIDER_KNOB_DISABLED_COLOR = 0xFF47252A;
            }
            case PURPLE -> {
                UI_PANEL_COLOR = 0xFF120F18;
                UI_PANEL_HOVER_COLOR = 0xFF1D1826;
                UI_BORDER_COLOR = 0xFFE7DDF7;
                UI_BORDER_DARK = 0xFF050308;
                UI_BUTTON_TEXT_COLOR = 0xFFF5F0FF;
                UI_SUBTEXT_COLOR = 0xFFAA9EC3;
                MOD_TILE_ENABLED_COLOR = 0xFF62418E;
                MOD_TILE_ENABLED_FOOTER = 0xFF4B326E;
                MOD_TILE_DISABLED_COLOR = 0xFF120F18;
                MOD_TILE_DISABLED_FOOTER = 0xFF0D0A12;
                MOD_TILE_DISABLED_BORDER = 0xFF050308;
                BUTTON_FILL_COLOR = 0xFF2B2238;
                BUTTON_FILL_HOVER_COLOR = 0xFF392C4B;
                BUTTON_BORDER_COLOR = 0xFFE7DDF7;
                BUTTON_BORDER_HOVER_COLOR = 0xFFF5F0FF;
                BACKDROP_TOP_COLOR = 0x880C0911;
                BACKDROP_BOTTOM_COLOR = 0xBB040307;
                LAYOUT_BACKDROP_TOP_COLOR = 0x440C0911;
                LAYOUT_BACKDROP_BOTTOM_COLOR = 0x66050308;
                DIVIDER_COLOR = 0x663A304D;
                SCROLL_TRACK_COLOR = 0x66140F1F;
                SCROLL_THUMB_COLOR = 0xCCE7DDF7;
                MODAL_OVERLAY_COLOR = 0x90060308;
                MODAL_PANEL_COLOR = 0xFF120F18;
                MODAL_BORDER_COLOR = 0xFFE7DDF7;
                MODAL_TEXT_COLOR = 0xFFE7DDF7;
                MODAL_DISABLED_TEXT_COLOR = 0xFF9387AB;
                MODAL_SECTION_COLOR = 0xFF9A78E8;
                MODAL_SECTION_DIVIDER_COLOR = 0xFF544A66;
                MODAL_CONTROL_FILL_COLOR = 0xFF2B2238;
                MODAL_CONTROL_HOVER_COLOR = 0xFF392C4B;
                MODAL_TOGGLE_ON_COLOR = 0xFF7B53B9;
                MODAL_TOGGLE_OFF_COLOR = 0xFF746A89;
                MODAL_DISABLED_FILL_COLOR = 0xFF392C4B;
                MODAL_VALUE_TEXT_COLOR = 0xFFCFC4E7;
                SLIDER_FILL_COLOR = 0xFF231D30;
                SLIDER_DISABLED_FILL_COLOR = 0xFF171320;
                SLIDER_KNOB_COLOR = 0xFFAC8CFF;
                SLIDER_KNOB_DISABLED_COLOR = 0xFF392C4B;
            }
            case MINECRAFT_DEFAULT -> {
                UI_PANEL_COLOR = 0xFF161616;
                UI_PANEL_HOVER_COLOR = 0xFF242424;
                UI_BORDER_COLOR = 0xFFA0A0A0;
                UI_BORDER_DARK = 0xFF000000;
                UI_BUTTON_TEXT_COLOR = 0xFFE0E0E0;
                UI_SUBTEXT_COLOR = 0xFFB0B0B0;
                MOD_TILE_ENABLED_COLOR = 0xFF4A7A36;
                MOD_TILE_ENABLED_FOOTER = 0xFF355828;
                MOD_TILE_DISABLED_COLOR = 0xFF161616;
                MOD_TILE_DISABLED_FOOTER = 0xFF101010;
                MOD_TILE_DISABLED_BORDER = 0xFF000000;
                BUTTON_FILL_COLOR = 0xFF5B5B5B;
                BUTTON_FILL_HOVER_COLOR = 0xFF6A6A6A;
                BUTTON_BORDER_COLOR = 0xFFA0A0A0;
                BUTTON_BORDER_HOVER_COLOR = 0xFFE0E0E0;
                BACKDROP_TOP_COLOR = 0xAA2A1E16;
                BACKDROP_BOTTOM_COLOR = 0xCC100A05;
                LAYOUT_BACKDROP_TOP_COLOR = 0x662A1E16;
                LAYOUT_BACKDROP_BOTTOM_COLOR = 0x88201010;
                DIVIDER_COLOR = 0x66404040;
                SCROLL_TRACK_COLOR = 0x66404040;
                SCROLL_THUMB_COLOR = 0xCCE0E0E0;
                MODAL_OVERLAY_COLOR = 0x90000000;
                MODAL_PANEL_COLOR = 0xFF161616;
                MODAL_BORDER_COLOR = 0xFFA0A0A0;
                MODAL_TEXT_COLOR = 0xFFE0E0E0;
                MODAL_DISABLED_TEXT_COLOR = 0xFF707070;
                MODAL_SECTION_COLOR = 0xFFE0E0A0;
                MODAL_SECTION_DIVIDER_COLOR = 0xFF4A4A4A;
                MODAL_CONTROL_FILL_COLOR = 0xFF5B5B5B;
                MODAL_CONTROL_HOVER_COLOR = 0xFF6A6A6A;
                MODAL_TOGGLE_ON_COLOR = 0xFF6D9E46;
                MODAL_TOGGLE_OFF_COLOR = 0xFF7A7A7A;
                MODAL_DISABLED_FILL_COLOR = 0xFF5B5B5B;
                MODAL_VALUE_TEXT_COLOR = 0xFFB0B0B0;
                SLIDER_FILL_COLOR = 0xFF4A4A4A;
                SLIDER_DISABLED_FILL_COLOR = 0xFF363636;
                SLIDER_KNOB_COLOR = 0xFFA0A0A0;
                SLIDER_KNOB_DISABLED_COLOR = 0xFF6A6A6A;
            }
        }
    }

    private float sliderValueForMouse(HudWidget.HudSetting setting, int barX, int barW, double mouseX) {
        float t = (float) ((mouseX - barX) / (double) barW);
        t = Math.max(0f, Math.min(1f, t));

        float raw = setting.min() + t * (setting.max() - setting.min());
        float snapped = (setting.step() > 0f) ? (Math.round(raw / setting.step()) * setting.step()) : raw;
        return Math.max(setting.min(), Math.min(setting.max(), snapped));
    }

    private int modalHeight() {
        int desiredHeight = Math.max(MODAL_MIN_H, 28 + settingsContentHeight() + MODAL_PAD);
        int availableHeight = Math.max(100, this.height - 20);
        return Math.min(desiredHeight, availableHeight);
    }

    private int modalWidth() {
        int desiredWidth = Math.max(MODAL_MIN_W, settingsRequiredModalWidth());
        int availableWidth = Math.max(MODAL_MIN_W, this.width - 20);
        return Math.min(desiredWidth, availableWidth);
    }

    private int modalX() {
        int base = (this.width - modalWidth()) / 2;
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
        int x = modalX();
        int y = modalY();

        int startX = x + MODAL_PAD;
        int startY = y + 28 - settingsScroll;
        int nestedStartX = startX + 10;
        settingsCursorY = startY;

        int btnW = SETTINGS_CONTROL_W;
        int btnH = SETTINGS_CONTROL_H;
        int rightEdge = x + modalWidth() - MODAL_PAD;
        int resetX = rightEdge - RESET_W;
        int controlsRight = resetX - SETTINGS_RESET_GAP;
        int btnX = controlsRight - btnW;
        int toggleW = SETTINGS_TOGGLE_W;
        int toggleX = controlsRight - toggleW;
        int sliderBarW = SETTINGS_SLIDER_W;
        int sliderBarX = controlsRight - sliderBarW;

        return new SettingsLayout(
                startX, startY, nestedStartX,
                btnW, btnH, btnX,
                toggleW, toggleX,
                resetX,
                sliderBarX, sliderBarW
        );
    }

    private int nextSettingY(SettingsLayout layout, HudWidget.HudSetting setting) {
        int rowY = settingsCursorY;
        settingsCursorY += settingBlockHeight(setting) + SETTINGS_ROW_GAP;
        return rowY;
    }

    private int settingBlockHeight(HudWidget.HudSetting setting) {
        if (setting.type() != HudWidget.HudSetting.Type.CUSTOM_LIST || setting.customList() == null) {
            return SETTINGS_ROW_H;
        }

        int entryCount = setting.customList().getEntries().get().size();
        return SETTINGS_ROW_H + entryCount * (CUSTOM_ENTRY_H + CUSTOM_ENTRY_GAP);
    }

    private int settingsContentHeight() {
        if (settingsWidget == null) {
            return 0;
        }

        List<HudWidget.HudSetting> settings = safeSettings(settingsWidget);
        int height = 0;
        for (HudWidget.HudSetting setting : settings) {
            height += settingBlockHeight(setting);
        }
        height += Math.max(0, settings.size() - 1) * SETTINGS_ROW_GAP;
        return height;
    }

    private int settingsRequiredModalWidth() {
        int requiredWidth = MODAL_MIN_W;
        if (settingsWidget == null || textRenderer == null) {
            return requiredWidth;
        }

        int titleWidth = textRenderer.getWidth(settingsWidget.getName() + " Settings");
        requiredWidth = Math.max(requiredWidth, MODAL_PAD + titleWidth + 24 + MODAL_PAD);

        boolean grouped = false;
        for (HudWidget.HudSetting setting : safeSettings(settingsWidget)) {
            int labelStart = grouped ? MODAL_PAD + 10 : MODAL_PAD;
            int labelWidth = textRenderer.getWidth(setting.label());
            int rightReservedWidth = switch (setting.type()) {
                case TOGGLE -> MODAL_PAD + RESET_W + SETTINGS_RESET_GAP + SETTINGS_TOGGLE_W;
                case COLOR -> MODAL_PAD + RESET_W + SETTINGS_RESET_GAP + SETTINGS_CONTROL_W + SETTINGS_COLOR_SWATCH_W;
                case SLIDER -> MODAL_PAD + RESET_W + SETTINGS_RESET_GAP + SETTINGS_SLIDER_W;
                case DROPDOWN -> MODAL_PAD + RESET_W + SETTINGS_RESET_GAP + SETTINGS_CONTROL_W;
                case CUSTOM_LIST -> MODAL_PAD + RESET_W + SETTINGS_RESET_GAP + CUSTOM_ADD_W;
                case SECTION -> MODAL_PAD + RESET_W + SETTINGS_RESET_GAP;
            };
            int gap = setting.type() == HudWidget.HudSetting.Type.SECTION ? 6 : MODAL_LABEL_CONTROL_GAP;
            requiredWidth = Math.max(requiredWidth, labelStart + labelWidth + gap + rightReservedWidth);
            if (setting.type() == HudWidget.HudSetting.Type.SECTION) {
                grouped = true;
            }
        }

        return requiredWidth;
    }

    private Rect settingsViewportBounds() {
        int x = modalX();
        int y = modalY();
        return new Rect(x + 1, y + 26, modalWidth() - 2, Math.max(1, modalHeight() - 27));
    }

    private void updateSettingsScrollBounds() {
        if (settingsWidget == null) {
            settingsScroll = 0;
            settingsMaxScroll = 0;
            return;
        }

        int visibleContentHeight = Math.max(1, modalHeight() - 29);
        settingsMaxScroll = Math.max(0, settingsContentHeight() - visibleContentHeight);
        settingsScroll = MathHelper.clamp(settingsScroll, 0, settingsMaxScroll);
    }

    private int customAddX(SettingsLayout layout) {
        return layout.resetX - 4 - CUSTOM_ADD_W;
    }

    private Rect customEntryBounds(SettingsLayout layout, int rowY, int index) {
        int cardY = rowY + SETTINGS_ROW_H + CUSTOM_ENTRY_GAP
                + index * (CUSTOM_ENTRY_H + CUSTOM_ENTRY_GAP);
        int cardWidth = modalWidth() - MODAL_PAD * 2;
        return new Rect(layout.startX, cardY, cardWidth, CUSTOM_ENTRY_H);
    }

    private Rect customRemoveBounds(Rect card) {
        return new Rect(card.right() - 16, card.y + 6, 12, 12);
    }

    private static String safeCustomText(java.util.function.Function<HudWidget.CustomEntry, String> formatter,
                                         HudWidget.CustomEntry entry) {
        if (formatter == null) {
            return "";
        }
        String value = formatter.apply(entry);
        return value == null ? "" : value;
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

    private enum GeneralTab {
        LOOKS_AND_FEEL,
        KEYBINDS
    }

    private enum EditorTheme {
        DEFAULT("default", "Default"),
        MIDNIGHT("midnight", "Midnight"),
        DUSK("dusk", "Dusk"),
        RED("red", "Red"),
        PURPLE("purple", "Purple"),
        MINECRAFT_DEFAULT("minecraft_default", "Minecraft Default");

        private final String id;
        private final String label;

        EditorTheme(String id, String label) {
            this.id = id;
            this.label = label;
        }

        private static EditorTheme fromId(String id) {
            for (EditorTheme theme : values()) {
                if (theme.id.equalsIgnoreCase(id)) {
                    return theme;
                }
            }
            return DEFAULT;
        }
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

    private record GridMetrics(int tileSize, int gap, int columns, int rows, int startX, int startY, int gridWidth,
                               int gridHeight) {
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
