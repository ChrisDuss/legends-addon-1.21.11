package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.LegendsAddon;
import legends.ultra.cool.addons.update.UpdateManager;
import legends.ultra.cool.addons.update.UpdateManifest;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Unique
    private static final Identifier LEGENDS_TITLE_TEXTURE =
            Identifier.of(LegendsAddon.MOD_ID, "textures/legends_addon_title.png");
    @Unique
    private static final int TITLE_TEXTURE_WIDTH = 1024;
    @Unique
    private static final int TITLE_TEXTURE_HEIGHT = 256;
    @Unique
    private static final int TITLE_VISIBLE_HEIGHT = 176;
    @Unique
    private static final int TITLE_TOP_MARGIN = 10;
    @Unique
    private static final int TITLE_SIDE_MARGIN = 24;
    @Unique
    private static final int TITLE_MIN_WIDTH = 340;
    @Unique
    private static final int TITLE_MAX_WIDTH = 680;
    @Unique
    private ButtonWidget legends$updateButton;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void legends$addUpdateButton(CallbackInfo ci) {
        int buttonY = this.height / 4 + 48 + 96 + 20;
        this.legends$updateButton = ButtonWidget.builder(UpdateManager.getButtonText(), button -> legends$handleUpdateButton())
                .dimensions(this.width / 2 - 100, buttonY, 200, 20)
                .build();
        this.legends$updateButton.active = UpdateManager.isButtonActive();
        addDrawableChild(this.legends$updateButton);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void legends$refreshUpdateButton(CallbackInfo ci) {
        if (this.legends$updateButton == null) {
            return;
        }

        this.legends$updateButton.setMessage(UpdateManager.getButtonText());
        this.legends$updateButton.active = UpdateManager.isButtonActive();
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/LogoDrawer;draw(Lnet/minecraft/client/gui/DrawContext;IF)V"
            )
    )
    private void legends$replaceVanillaTitle(LogoDrawer logoDrawer, DrawContext ctx, int screenWidth, float alpha) {
        int maxAllowedWidth = Math.max(TITLE_MIN_WIDTH, this.width - TITLE_SIDE_MARGIN);
        int drawWidth = Math.min(TITLE_MAX_WIDTH, maxAllowedWidth);
        drawWidth = Math.max(TITLE_MIN_WIDTH, Math.min(drawWidth, Math.round(this.width * 0.54f)));
        int drawHeight = Math.round(drawWidth * (TITLE_VISIBLE_HEIGHT / (float) TITLE_TEXTURE_WIDTH));

        int x = (this.width - drawWidth) / 2;
        int y = TITLE_TOP_MARGIN;
        int color = ColorHelper.getWhite(logoDrawer.shouldIgnoreAlpha() ? 1.0f : alpha);

        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                LEGENDS_TITLE_TEXTURE,
                x,
                y,
                0.0f,
                0.0f,
                drawWidth,
                drawHeight,
                TITLE_TEXTURE_WIDTH,
                TITLE_VISIBLE_HEIGHT,
                TITLE_TEXTURE_WIDTH,
                TITLE_TEXTURE_HEIGHT,
                color
        );
    }

    @Unique
    private void legends$handleUpdateButton() {
        if (this.client == null) {
            return;
        }

        if (!UpdateManager.canDownloadUpdate()) {
            UpdateManager.checkForUpdates();
            return;
        }

        UpdateManifest manifest = UpdateManager.getAvailableManifest();
        if (manifest == null) {
            return;
        }

        Screen titleScreen = (Screen) (Object) this;
        Text title = Text.literal("Update Legends Addon?");
        Text message = Text.literal("Install " + UpdateManager.getInstalledVersion() + " -> " + manifest.version()
                + ". Restart Minecraft after the download finishes.");

        this.client.setScreen(new ConfirmScreen(confirmed -> {
            this.client.setScreen(titleScreen);
            if (confirmed) {
                UpdateManager.downloadAndInstall(this.client);
            }
        }, title, message, Text.literal("Download"), Text.literal("Cancel")));
    }
}
