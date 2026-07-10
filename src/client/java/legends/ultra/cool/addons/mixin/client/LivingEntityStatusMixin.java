package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.hud.widget.MobKillTracker;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityStatusMixin {
    @Inject(method = "handleStatus", at = @At("HEAD"))
    private void legends$recordMobTrackerDeathStatus(byte status, CallbackInfo ci) {
        MobKillTracker.recordDeathStatus((LivingEntity) (Object) this, status);
    }
}
