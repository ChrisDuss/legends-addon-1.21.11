package legends.ultra.cool.addons.storage;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class VaultRenameScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 132;

    private final Screen parent;
    private final int vaultNumber;
    private TextFieldWidget nameField;

    public VaultRenameScreen(Screen parent, int vaultNumber) {
        super(Text.literal("Rename Vault"));
        this.parent = parent;
        this.vaultNumber = vaultNumber;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        this.nameField = new TextFieldWidget(this.textRenderer, left + 20, top + 44, PANEL_WIDTH - 40, 20, Text.literal("Vault Name"));
        this.nameField.setMaxLength(30);
        this.nameField.setPlaceholder(Text.literal("Enter a vault name"));
        this.nameField.setText(VaultStorageManager.getCustomName(this.vaultNumber));
        this.addDrawableChild(this.nameField);
        this.setFocused(this.nameField);
        this.setInitialFocus(this.nameField);
        this.nameField.setFocused(true);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> saveAndClose())
                .dimensions(left + 20, top + 84, 70, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), button -> {
                    this.nameField.setText("");
                    saveAndClose();
                })
                .dimensions(left + 105, top + 84, 70, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(left + 190, top + 84, 70, 20)
                .build());
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
        int top = (this.height - PANEL_HEIGHT) / 2;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;

        context.fillGradient(left, top, right, bottom, 0xF0221C28, 0xF0100D12);
        context.fill(left, top, right, top + 1, 0xFFD84E45);
        context.fill(left, top + 1, left + 1, bottom, 0xFFD84E45);
        context.fill(right - 1, top + 1, right, bottom, 0xFF59211B);
        context.fill(left + 1, bottom - 1, right, bottom, 0xFF59211B);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Rename Vault #" + this.vaultNumber), this.width / 2, top + 14, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("This is local to your client."), left + 20, top + 30, 0xB8B8B8);

        String previewName = this.nameField == null ? "" : this.nameField.getText();
        String preview = previewName.isBlank() ? VaultStorageManager.getDefaultName(this.vaultNumber) : previewName.trim();
        context.drawTextWithShadow(this.textRenderer, Text.literal("Preview: " + preview), left + 20, top + 110, 0xE86A5C);

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void saveAndClose() {
        VaultStorageManager.setCustomName(this.vaultNumber, this.nameField == null ? "" : this.nameField.getText());
        close();
    }
}
