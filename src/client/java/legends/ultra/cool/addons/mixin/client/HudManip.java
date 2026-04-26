package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.widget.Defense;
import legends.ultra.cool.addons.hud.widget.Health;
import legends.ultra.cool.addons.hud.widget.Mana;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class HudManip {
    private Text legends$overlayBackup;

    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
    private void legends$hideHealthBar(DrawContext context, PlayerEntity player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        if (Health.isEnabledGlobal()) {
            if (WidgetConfigManager.getBool("Health Display", "heartsToggle", false)) ci.cancel();
        }
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void legends$hideFoodBar(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo ci) {
        if (Mana.isEnabledGlobal()) {
            if (WidgetConfigManager.getBool("Mana Display", "foodToggle", false)) ci.cancel();
        }
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"))
    private void legends$stripHealthOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        InGameHudAccessor accessor = (InGameHudAccessor) this;
        Text overlay = accessor.legends$getOverlayMessage();
        legends$overlayBackup = overlay;
        Text stripped = Health.stripHealthOverlay(overlay);
        stripped = Mana.stripManaOverlay(stripped);
        stripped = Defense.stripDefenseOverlay(stripped);
        accessor.legends$setOverlayMessage(stripped);
    }

    @Inject(method = "renderOverlayMessage", at = @At("RETURN"))
    private void legends$restoreOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ((InGameHudAccessor) this).legends$setOverlayMessage(legends$overlayBackup);
        legends$overlayBackup = null;
    }
}
