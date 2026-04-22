package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.hud.widget.Health;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class HudManip {
    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
    private void legends$hideHealthBar(DrawContext context, PlayerEntity player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        if (Health.barToggle) {
            if (Health.isEnabledGlobal()) ci.cancel();
        }
    }
}
