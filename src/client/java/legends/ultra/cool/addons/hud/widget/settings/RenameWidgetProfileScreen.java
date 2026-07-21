package legends.ultra.cool.addons.hud.widget.settings;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class RenameWidgetProfileScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 118;
    private static final int FIELD_HEIGHT = 20;

    private final Screen parent;
    private final String profileId;
    private final String currentName;
    private TextFieldWidget nameInput;
    private String errorMessage = "";

    public RenameWidgetProfileScreen(Screen parent, String profileId, String currentName) {
        super(Text.literal("Rename Profile"));
        this.parent = parent;
        this.profileId = profileId;
        this.currentName = currentName == null ? "" : currentName;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = panelTop();

        nameInput = new TextFieldWidget(
                this.textRenderer,
                left + 20,
                top + 42,
                PANEL_WIDTH - 40,
                FIELD_HEIGHT,
                Text.literal("Profile name")
        );
        nameInput.setMaxLength(32);
        nameInput.setText(currentName);
        addDrawableChild(nameInput);

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> save())
                .dimensions(left + 48, top + PANEL_HEIGHT - 30, 68, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(left + 144, top + PANEL_HEIGHT - 30, 68, 20)
                .build());

        setFocused(nameInput);
        setInitialFocus(nameInput);
        nameInput.setFocused(true);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        MinecraftClient client = this.client;
        if (client != null) {
            client.setScreen(parent);
        }
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
        int bottom = top + PANEL_HEIGHT;

        context.fillGradient(left, top, left + PANEL_WIDTH, bottom, 0xF0221C28, 0xF0100D12);
        drawBorder(context, left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFD0D0D0);
        context.drawCenteredTextWithShadow(textRenderer, title, this.width / 2, top + 12, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("Name"), left + 20, top + 30, 0xFFC8C8C8);

        if (!errorMessage.isBlank()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(errorMessage), this.width / 2, bottom - 45, 0xFFFF6666);
        }

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void save() {
        String nextName = nameInput == null ? "" : nameInput.getText().trim();
        if (nextName.isBlank()) {
            errorMessage = "Name is required.";
            return;
        }

        if (!WidgetConfigManager.renameProfile(profileId, nextName)) {
            errorMessage = "Name already exists.";
            return;
        }

        close();
    }

    private int panelTop() {
        return Math.max(8, (this.height - PANEL_HEIGHT) / 2);
    }

    private static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.drawVerticalLine(x, y, y + height, color);
        context.drawVerticalLine(x + width, y, y + height, color);
        context.drawHorizontalLine(x, x + width, y, color);
        context.drawHorizontalLine(x, x + width, y + height, color);
    }
}
