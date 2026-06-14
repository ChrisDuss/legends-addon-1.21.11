package legends.ultra.cool.addons.storage;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class WardrobeSetEditScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 188;

    private final Screen parent;
    private final int setIndex;
    private TextFieldWidget nameField;
    private TextFieldWidget colorField;
    private String errorMessage = "";

    public WardrobeSetEditScreen(Screen parent, int setIndex) {
        super(Text.literal("Edit Wardrobe Set"));
        this.parent = parent;
        this.setIndex = setIndex;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = panelTop();

        this.nameField = new TextFieldWidget(
                this.textRenderer,
                left + 20,
                top + 45,
                PANEL_WIDTH - 40,
                20,
                Text.literal("Set name")
        );
        this.nameField.setMaxLength(30);
        this.nameField.setText(WardrobeManager.getSetCustomLabel(this.setIndex));
        this.nameField.setChangedListener(value -> this.errorMessage = "");
        this.addDrawableChild(this.nameField);

        this.colorField = new TextFieldWidget(
                this.textRenderer,
                left + 20,
                top + 86,
                PANEL_WIDTH - 40,
                20,
                Text.literal("Background color")
        );
        this.colorField.setMaxLength(9);
        this.colorField.setTextPredicate(WardrobeSetEditScreen::isPotentialColor);
        this.colorField.setText(formatColor(WardrobeManager.getSetBackgroundColor(this.setIndex)));
        this.colorField.setChangedListener(value -> this.errorMessage = "");
        this.addDrawableChild(this.colorField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> saveAndClose())
                .dimensions(left + 20, top + 158, 80, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), button -> {
                    this.nameField.setText("");
                    this.colorField.setText("");
                    this.errorMessage = "";
                })
                .dimensions(left + 120, top + 158, 80, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(left + 220, top + 158, 80, 20)
                .build());

        this.setFocused(this.nameField);
        this.setInitialFocus(this.nameField);
        this.nameField.setFocused(true);
    }

    @Override
    public void close() {
        MinecraftClient client = this.client;
        if (client != null) {
            client.setScreen(this.parent);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            saveAndClose();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fillGradient(0, 0, this.width, this.height, 0xD0100E14, 0xE008080A);

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = panelTop();
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;

        context.fillGradient(left, top, right, bottom, 0xF0221C28, 0xF0100D12);
        drawBorder(context, left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFD0D0D0);
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Edit " + WardrobeManager.getSetDisplayName(this.setIndex)),
                this.width / 2,
                top + 12,
                0xFFFFFFFF
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Set name (optional)"),
                left + 20,
                top + 32,
                0xFFC8C8C8
        );
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Background color (optional)"),
                left + 20,
                top + 73,
                0xFFC8C8C8
        );
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("#RRGGBB or #AARRGGBB"),
                right - 20 - this.textRenderer.getWidth("#RRGGBB or #AARRGGBB"),
                top + 73,
                0xFF888888
        );

        Integer parsedColor = parseColor(this.colorField == null ? "" : this.colorField.getText());
        int previewLeft = left + 20;
        int previewTop = top + 119;
        int previewRight = right - 20;
        int previewBottom = previewTop + 25;
        if (parsedColor == null || parsedColor == 0) {
            context.fillGradient(previewLeft, previewTop, previewRight, previewBottom, 0xE0201A24, 0xE0100D12);
        } else {
            context.fill(previewLeft, previewTop, previewRight, previewBottom, parsedColor);
        }
        drawBorder(
                context,
                previewLeft,
                previewTop,
                previewRight - previewLeft,
                previewBottom - previewTop,
                parsedColor == null ? 0xFFFF6666 : 0xFF7A2A24
        );

        String name = this.nameField == null ? "" : this.nameField.getText().trim();
        String previewName = name.isBlank() ? "Set " + (this.setIndex + 1) : name;
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal(previewName),
                this.width / 2,
                previewTop + 8,
                0xFFFFFFFF
        );

        if (!this.errorMessage.isBlank()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(this.errorMessage),
                    this.width / 2,
                    top + 147,
                    0xFFFF6666
            );
        }

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private int panelTop() {
        return Math.max(8, (this.height - PANEL_HEIGHT) / 2);
    }

    private void saveAndClose() {
        Integer color = parseColor(this.colorField == null ? "" : this.colorField.getText());
        if (color == null) {
            this.errorMessage = "Use #RRGGBB or #AARRGGBB.";
            this.setFocused(this.colorField);
            this.colorField.setFocused(true);
            return;
        }

        MinecraftClient client = this.client;
        if (client != null) {
            WardrobeManager.updateSetAppearance(
                    client,
                    this.setIndex,
                    this.nameField == null ? "" : this.nameField.getText(),
                    color
            );
        }
        close();
    }

    private static boolean isPotentialColor(String value) {
        return value.isEmpty() || value.matches("#?[0-9a-fA-F]{0,8}");
    }

    private static Integer parseColor(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return 0;
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() != 6 && normalized.length() != 8) {
            return null;
        }

        try {
            long parsed = Long.parseUnsignedLong(normalized, 16);
            return normalized.length() == 6
                    ? (int) (0xFF000000L | parsed)
                    : (int) parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String formatColor(int color) {
        if (color == 0) {
            return "";
        }
        if ((color >>> 24) == 0xFF) {
            return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
        }
        return String.format(Locale.ROOT, "#%08X", color);
    }

    private static void drawBorder(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        context.drawVerticalLine(x, y, y + height, color);
        context.drawVerticalLine(x + width, y, y + height, color);
        context.drawHorizontalLine(x, x + width, y, color);
        context.drawHorizontalLine(x, x + width, y + height, color);
    }
}
