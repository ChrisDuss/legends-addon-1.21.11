package legends.ultra.cool.addons.hud;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.widget.settings.ColorPicker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

public class HudEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 120;
    private static final int HEADER_HEIGHT = 16;
    private static final int ROW_HEIGHT = 14;

    private static final int MODAL_W = 220;
    private static final int MODAL_MIN_H = 180;
    private static final int MODAL_PAD = 8;

    private static final int SETTINGS_ROW_H = 16;
    private static final int SETTINGS_ROW_GAP = 6;

    private static final int RESET_W = 12;
    private static final int RESET_H = 12;

    private HudWidget dragging;
    private BarDraggable draggingBar;
    private double lastMouseX, lastMouseY;
    private boolean panelExpanded = true;

    private HudWidget settingsWidget = null; // null = closed

    // Color picker state
    private ColorPicker colorPicker = null;
    private String openColorKey = null;

    // Slider dragging state
    private String draggingSliderKey = null;

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
    }

    private boolean isSettingsOpen() {
        return settingsWidget != null;
    }

    private int panelX() {
        return this.width - PANEL_WIDTH;
    }

    private int modalX() {
        int base = (this.width - MODAL_W) / 2;

        // if picker open, shift modal slightly so the pair feels centered
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

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static List<HudWidget.HudSetting> safeSettings(HudWidget w) {
        return Optional.ofNullable(w.getSettings()).orElse(List.of());
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

    private BarDraggable getBarUnderMouse(double mouseX, double mouseY) {
        for (HudWidget widget : HudManager.getWidgets()) {
            if (!widget.isEnabled()) continue;
            if (widget instanceof BarDraggable bar && bar.isMouseOverBar(mouseX, mouseY)) {
                return bar;
            }
        }

        return null;
    }

    // -----------------------------
    // Mouse input (1.21.11 signatures)
    // -----------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Modal handles input first
        if (isSettingsOpen()) {
            if (colorPicker != null && colorPicker.mouseClicked(mouseX, mouseY, button)) return true;
            return handleSettingsClick(mouseX, mouseY, button);
        }

        int x = panelX();

        // Header (collapse/expand)
        int headerLeft = panelExpanded ? x : x + PANEL_WIDTH - HEADER_HEIGHT;
        int headerRight = x + PANEL_WIDTH;

        if (mouseX >= headerLeft && mouseX <= headerRight && mouseY >= 0 && mouseY <= HEADER_HEIGHT) {
            panelExpanded = !panelExpanded;
            return true;
        }

        // Panel collapsed: only drag widgets
        if (!panelExpanded) {
            BarDraggable bar = getBarUnderMouse(mouseX, mouseY);
            if (bar != null) {
                draggingBar = bar;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                return true;
            }

            for (HudWidget widget : HudManager.getWidgets()) {
                if (!widget.isEnabled()) continue;
                if (widget.isMouseOver(mouseX, mouseY)) {
                    dragging = widget;
                    lastMouseX = mouseX;
                    lastMouseY = mouseY;
                    return true;
                }
            }
            return super.mouseClicked(new Click(mouseX, mouseY, new MouseInput(button, 0)), doubled);
        }

        // Panel list clicks
        int y = HEADER_HEIGHT + 4;
        for (HudWidget widget : HudManager.getWidgets()) {
            if (widget == null) continue;

            int x1 = x + 5;
            int y1 = y;
            int x2 = widget.hasSettings() ? x + PANEL_WIDTH - ROW_HEIGHT - 5 : x + PANEL_WIDTH - 5 ;
            int y2 = y + ROW_HEIGHT;

            // toggle enable
            if (inside(mouseX, mouseY, x1, y1, x2 - x1, y2 - y1)) {
                widget.toggle();
                WidgetConfigManager.updateWidget(widget);
                return true;
            }

            if (widget.hasSettings()) {
                // settings button area
                int sx1 = x + PANEL_WIDTH - ROW_HEIGHT - 5;
                int sy1 = y;
                int sx2 = x + PANEL_WIDTH - 5;
                int sy2 = y + ROW_HEIGHT;

                if (inside(mouseX, mouseY, sx1, sy1, sx2 - sx1, sy2 - sy1)) {
                    settingsWidget = widget;
                    colorPicker = null;
                    openColorKey = null;
                    draggingSliderKey = null;
                    return true;
                }
            }

            y += ROW_HEIGHT + 2;
        }

        // Canvas dragging selection
        BarDraggable bar = getBarUnderMouse(mouseX, mouseY);
        if (bar != null) {
            draggingBar = bar;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        for (HudWidget widget : HudManager.getWidgets()) {
            if (!widget.isEnabled()) continue;
            if (widget.isMouseOver(mouseX, mouseY)) {
                dragging = widget;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Modal dragging (picker / slider)
        if (isSettingsOpen()) {
            if (colorPicker != null && colorPicker.mouseDragged(mouseX, mouseY, button, dx, dy)) return true;

            if (draggingSliderKey != null) {
                SettingsLayout l = beginSettingsLayout();
                for (HudWidget.HudSetting s : safeSettings(settingsWidget)) {
                    int rowY = nextRowY(l);

                    if (s.type() != HudWidget.HudSetting.Type.SLIDER) continue;
                    if (!s.key().equals(draggingSliderKey)) continue;
                    if (!s.enabled().getAsBoolean()) continue;

                    int barX = l.sliderBarX;
                    int barW = l.sliderBarW;

                    float t = (float) ((mouseX - barX) / (double) barW);
                    t = Math.max(0f, Math.min(1f, t));

                    float raw = s.min() + t * (s.max() - s.min());
                    float snapped = (s.step() > 0f) ? (Math.round(raw / s.step()) * s.step()) : raw;
                    snapped = Math.max(s.min(), Math.min(s.max(), snapped));

                    s.setFloat().accept(snapped);
                    WidgetConfigManager.updateWidget(settingsWidget);
                    return true;
                }
            }

            return false;
        }

        // Normal canvas dragging
        if (draggingBar != null) {
            draggingBar.moveBar(dx, dy);
            draggingBar.clampBar(this.width, this.height);
            return true;
        }

        if (dragging != null) {
            dragging.x += dx;
            dragging.y += dy;

            dragging.x = Math.max(0, Math.min(dragging.x, this.width - dragging.getWidth()));
            dragging.y = Math.max(0, Math.min(dragging.y, this.height - dragging.getHeight()));
            return true;
        }

        return super.mouseDragged(click, dx, dy);
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
                return true;
            }
            return true;
        }

        if (draggingBar != null) {
            draggingBar.saveBarPosition();
            draggingBar = null;
            return true;
        }

        if (dragging != null) {
            WidgetConfigManager.updateWidget(dragging);
            dragging = null;
            return true;
        }

        return super.mouseReleased(click);
    }

    // -----------------------------
    // Settings modal logic
    // -----------------------------

    private void openColorPicker(HudWidget.HudSetting s, int px, int py, int pw) {
        if (openColorKey != null && openColorKey.equals(s.key())) {
            openColorKey = null;
            colorPicker = null;
            return;
        }

        openColorKey = s.key();
        colorPicker = new ColorPicker(
                px, py, pw,
                () -> s.getColor().getAsInt(),
                c -> {
                    s.setColor().accept(c);
                    WidgetConfigManager.updateWidget(settingsWidget);
                }
        );
    }

    private boolean handleSettingsClick(double mouseX, double mouseY, int button) {
        int x = modalX();
        int y = modalY();

        // Close X
        String close = "✕";
        int closeX = x + MODAL_W - 14;
        int closeY = y + 6;
        int cw = textRenderer.getWidth(close);
        int ch = textRenderer.fontHeight;

        if (inside(mouseX, mouseY, closeX, closeY, cw, ch)) {
            WidgetConfigManager.updateWidget(settingsWidget);
            settingsWidget = null;
            colorPicker = null;
            openColorKey = null;
            draggingSliderKey = null;
            return true;
        }

        SettingsLayout l = beginSettingsLayout();

        for (HudWidget.HudSetting s : safeSettings(settingsWidget)) {
            int rowY = nextRowY(l);

            if (s.type() == HudWidget.HudSetting.Type.SECTION) {
                continue;
            }

            // Reset per-row
            if (inside(mouseX, mouseY, l.resetX, rowY - 1, RESET_W, RESET_H)) {
                WidgetConfigManager.clearSetting(settingsWidget.getName(), s.key(), true);

                // Force re-apply defaults (by reading “default” and writing it once)
                switch (s.type()) {
                    case SECTION -> {
                    }
                    case TOGGLE -> s.setBool().accept(s.getBool().getAsBoolean());
                    case COLOR -> s.setColor().accept(s.getColor().getAsInt());
                    case SLIDER -> s.setFloat().accept((float) s.getFloat().getAsDouble());
                }

                if (openColorKey != null && openColorKey.equals(s.key())) {
                    openColorKey = null;
                    colorPicker = null;
                }
                if (draggingSliderKey != null && draggingSliderKey.equals(s.key())) {
                    draggingSliderKey = null;
                }

                WidgetConfigManager.updateWidget(settingsWidget);
                return true;
            }

            switch (s.type()) {
                case SECTION -> {
                }
                case TOGGLE -> {
                    if (!s.enabled().getAsBoolean()) break;

                    int pillY = rowY - 2;
                    if (inside(mouseX, mouseY, l.toggleX, pillY, l.toggleW, l.btnH)) {
                        boolean newVal = !s.getBool().getAsBoolean();
                        s.setBool().accept(newVal);
                        WidgetConfigManager.updateWidget(settingsWidget);

                        // If we turned off something while picker open, just close picker (safe default)
                        if (!newVal && openColorKey != null) {
                            openColorKey = null;
                            colorPicker = null;
                        }
                        return true;
                    }
                }

                case COLOR -> {
                    if (!s.enabled().getAsBoolean()) break;

                    int btnY = rowY - 2;
                    if (inside(mouseX, mouseY, l.btnX, btnY, l.btnW, l.btnH)) {
                        int gap = 6;
                        int pickerW = 180;

                        int px = modalX() + MODAL_W + gap;
                        int py = modalY();

                        if (px + pickerW > this.width) {
                            px = modalX() - pickerW - gap;
                        }

                        openColorPicker(s, px, py, pickerW);
                        return true;
                    }
                }

                case SLIDER -> {
                    if (!s.enabled().getAsBoolean()) break;

                    int barX = l.sliderBarX;
                    int barY = rowY - 1;
                    int barW = l.sliderBarW;
                    int barH = 12;

                    if (inside(mouseX, mouseY, barX, barY, barW, barH)) {
                        draggingSliderKey = s.key();

                        float t = (float) ((mouseX - barX) / (double) barW);
                        t = Math.max(0f, Math.min(1f, t));

                        float raw = s.min() + t * (s.max() - s.min());
                        float snapped = (s.step() > 0f) ? (Math.round(raw / s.step()) * s.step()) : raw;
                        snapped = Math.max(s.min(), Math.min(s.max(), snapped));

                        s.setFloat().accept(snapped);
                        WidgetConfigManager.updateWidget(settingsWidget);
                        return true;
                    }
                }
            }
        }

        return true;
    }

    // -----------------------------
    // Rendering
    // -----------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        for (HudWidget widget : HudManager.getWidgets()) {
            if (widget.isEnabled()) widget.render(ctx);
        }

        renderWidgetList(ctx);
        renderPanelHeader(ctx);
        renderSettingsModal(ctx, mouseX, mouseY);
    }

    private void renderPanelHeader(DrawContext ctx) {
        int x = panelX();

        ctx.fill(
                panelExpanded ? x : x + PANEL_WIDTH - HEADER_HEIGHT,
                0,
                x + PANEL_WIDTH,
                HEADER_HEIGHT,
                0xCC000000
        );

        String arrow = panelExpanded ? "▶" : "☰";
        ctx.drawText(MinecraftClient.getInstance().textRenderer,
                panelExpanded ? arrow + " Widgets" : arrow,
                panelExpanded ? x + 6 : x + PANEL_WIDTH - HEADER_HEIGHT + 6,
                4,
                0xFFFFFFFF,
                false
        );
    }

    private void renderWidgetList(DrawContext ctx) {
        int x = panelX();

        if (!panelExpanded) return;

        ctx.fill(
                x,
                HEADER_HEIGHT,
                x + PANEL_WIDTH,
                this.height,
                0xAA000000
        );

        int y = HEADER_HEIGHT + 4;
        for (HudWidget widget : HudManager.getWidgets()) {
            if (widget == null) continue;

            int bgColor = widget.isEnabled() ? 0xFF2ECC71 : 0xFF7F8C8D;

            ctx.fill(x + 5, y, x + PANEL_WIDTH - 5, y + ROW_HEIGHT, bgColor);

            ctx.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    widget.getName(),
                    x + 8,
                    y + 3,
                    0xFF000000,
                    false
            );

            if (widget.hasSettings()) {
                // settings button
                ctx.fill(
                        x + PANEL_WIDTH - ROW_HEIGHT - 5,
                        y,
                        x + PANEL_WIDTH - 5,
                        y + ROW_HEIGHT,
                        0xFF7F8C8D
                );
                ctx.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        "⚙",
                        x + PANEL_WIDTH - ROW_HEIGHT - 1,
                        y + 3,
                        0xFFFFFFFF,
                        false
                );
            }
            y += ROW_HEIGHT + 2;
        }

    }

    private void renderSettingsModal(DrawContext ctx, int mouseX, int mouseY) {
        if (settingsWidget == null) return;

        int x = modalX();
        int y = modalY();
        int modalHeight = modalHeight();

        // dim
        ctx.fill(0, 0, this.width, this.height, 0x88000000);

        // panel
        ctx.fill(x, y, x + MODAL_W, y + modalHeight, 0xFF111111);
        drawBorder(ctx, x, y, MODAL_W, modalHeight, 0xFFFFFFFF);

        // title
        ctx.drawText(textRenderer, settingsWidget.getName() + " Settings", x + 8, y + 8, 0xFFFFFFFF, false);

        // close
        String close = "✕";
        ctx.drawText(textRenderer, close, x + MODAL_W - 14, y + 6, 0xFFFFFFFF, false);

        SettingsLayout l = beginSettingsLayout();
        boolean grouped = false;

        for (HudWidget.HudSetting s : safeSettings(settingsWidget)) {
            int rowY = nextRowY(l);

            if (s.type() == HudWidget.HudSetting.Type.SECTION) {
                grouped = true;
                drawSectionRow(ctx, l, rowY, s.label());
                continue;
            }

            drawResetButton(ctx, l.resetX, rowY - 1, mouseX, mouseY);
            int labelX = grouped ? l.nestedStartX : l.startX;

            switch (s.type()) {
                case SECTION -> {
                }
                case TOGGLE -> {
                    boolean enabled = s.enabled().getAsBoolean();
                    boolean val = s.getBool().getAsBoolean();
                    ctx.drawText(textRenderer, s.label(), labelX, rowY, enabled ? 0xFFFFFFFF : 0xFF777777, false);
                    drawTogglePill(ctx, l.toggleX, rowY - 2, l.toggleW, l.btnH, val, enabled);
                }

                case COLOR -> {
                    boolean enabled = s.enabled().getAsBoolean();
                    int argb = s.getColor().getAsInt();
                    ctx.drawText(textRenderer, s.label(), labelX, rowY, enabled ? 0xFFFFFFFF : 0xFF777777, false);

                    if (enabled) {
                        drawPickButton(ctx, l.btnX, rowY - 2, l.btnW, l.btnH, mouseX, mouseY);
                        drawSwatch(ctx, l.btnX - 18, rowY - 1, argb);
                    } else {
                        ctx.drawText(textRenderer, "-", l.btnX + 26, rowY, 0xFF777777, false);
                    }
                }

                case SLIDER -> {
                    boolean enabled = s.enabled().getAsBoolean();
                    float val = (float) s.getFloat().getAsDouble();
                    ctx.drawText(textRenderer, s.label(), labelX, rowY, enabled ? 0xFFFFFFFF : 0xFF777777, false);
                    drawSliderRow(ctx, l, rowY, mouseX, mouseY, val, s.min(), s.max(), s.step(), enabled);
                }
            }
        }

        // picker
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

    // -----------------------------
    // Drawing helpers
    // -----------------------------

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
        if (!enabled) bg = 0xFF444444;

        ctx.fill(x, y, x + w, y + h, bg);
        drawBorder(ctx, x, y, w, h, 0xFF000000);

        int txt = enabled ? 0xFF000000 : 0xFF1A1A1A;
        ctx.drawText(textRenderer, on ? "ON" : "OFF", x + 10, y + 3, txt, false);
    }

    private void drawResetButton(DrawContext ctx, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, RESET_W, RESET_H);
        int bg = hovered ? 0xFF555555 : 0xFF333333;

        ctx.fill(x, y, x + RESET_W, y + RESET_H, bg);
        drawBorder(ctx,x, y, RESET_W, RESET_H, 0xFF000000);

        // Bigger icon without weird matrix calls
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x + 3, y + 1);
        ctx.getMatrices().scale(1.5f, 1.5f);
        ctx.drawText(textRenderer, "↺", 0, 0, 0xFFFFFFFF, false);
        ctx.getMatrices().popMatrix();
    }

    private void drawSectionRow(DrawContext ctx, SettingsLayout l, int rowY, String label) {
        ctx.drawText(textRenderer, label, l.startX, rowY, 0xFF8EC5FF, false);

        int dividerX = l.startX + textRenderer.getWidth(label) + 6;
        int dividerY = rowY + (textRenderer.fontHeight / 2);
        if (dividerX < l.resetX - 4) {
            ctx.drawHorizontalLine(dividerX, l.resetX - 4, dividerY, 0xFF2D2D2D);
        }
    }

    private void drawSliderRow(DrawContext ctx, SettingsLayout l, int rowY, int mouseX, int mouseY,
                               float value, float min, float max, float step, boolean enabled) {
        int barX = l.sliderBarX;
        int barY = rowY - 1;
        int barW = l.sliderBarW;
        int barH = 12;

        int bg = enabled ? 0xFF2A2A2A : 0xFF1F1F1F;
        ctx.fill(barX, barY, barX + barW, barY + barH, bg);
        drawBorder(ctx, barX, barY, barW, barH, 0xFF000000);

        float t = (max == min) ? 0f : (value - min) / (max - min);
        t = Math.max(0f, Math.min(1f, t));

        int knobX = barX + (int) (t * (barW - 4));
        ctx.fill(knobX, barY, knobX + 4, barY + barH, enabled ? 0xFF7F8C8D : 0xFF444444);

        String v = (step >= 1f) ? String.format("%.0f", value) : String.format("%.2f", value);
        ctx.drawText(textRenderer, v,
                barX + (barW / 2) - (textRenderer.getWidth(v) / 2),
                rowY + 1,
                enabled ? 0xFFAAAAAA : 0xFF777777,
                false
        );
    }

    // -----------------------------
    // Layout
    // -----------------------------

    private int settingsRowIndex = 0;

    private static final class SettingsLayout {
        final int startX, startY;
        final int nestedStartX;
        final int btnW, btnH, btnX;
        final int toggleW, toggleX;
        final int resetX;
        final int sliderBarX, sliderBarW;

        SettingsLayout(int startX, int startY, int nestedStartX,
                       int btnW, int btnH, int btnX,
                       int toggleW, int toggleX,
                       int resetX,
                       int sliderBarX, int sliderBarW) {
            this.startX = startX;
            this.startY = startY;
            this.nestedStartX = nestedStartX;
            this.btnW = btnW;
            this.btnH = btnH;
            this.btnX = btnX;
            this.toggleW = toggleW;
            this.toggleX = toggleX;
            this.resetX = resetX;
            this.sliderBarX = sliderBarX;
            this.sliderBarW = sliderBarW;
        }
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

    private int nextRowY(SettingsLayout l) {
        int rowY = l.startY + settingsRowIndex * (SETTINGS_ROW_H + SETTINGS_ROW_GAP);
        settingsRowIndex++;
        return rowY;
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.drawVerticalLine(x, y, y + h, color);
        ctx.drawVerticalLine(x + w, y, y + h, color);
        ctx.drawHorizontalLine(x, x + w, y, color);
        ctx.drawHorizontalLine(x, x + w, y + h, color);
    }
}
