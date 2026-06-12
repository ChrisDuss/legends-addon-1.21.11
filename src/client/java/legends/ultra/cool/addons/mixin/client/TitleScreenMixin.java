package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.LegendsAddon;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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

    protected TitleScreenMixin(Text title) {
        super(title);
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
}
