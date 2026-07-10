package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.hud.widget.MobKillTracker;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientInputMixin {
    @Inject(method = "doAttack", at = @At("HEAD"))
    private void legends$recordMobTrackerAttackInput(CallbackInfoReturnable<Boolean> cir) {
        MobKillTracker.recordMagicAction();
    }

    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void legends$recordMobTrackerItemUseInput(CallbackInfo ci) {
        MobKillTracker.recordMagicAction();
    }
}
