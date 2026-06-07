package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.hud.widget.UIToggle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.bar.ExperienceBar;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceBar.class)
public class ExperienceBarMixin {
    @Inject(method = "renderBar", at = @At("HEAD"), cancellable = true)
    private void legends$hideExperienceBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (UIToggle.shouldHideXpBar()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderAddons", at = @At("HEAD"), cancellable = true)
    private void legends$hideExperienceAddons(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (UIToggle.shouldHideXpBar()) {
            ci.cancel();
        }
    }
}
