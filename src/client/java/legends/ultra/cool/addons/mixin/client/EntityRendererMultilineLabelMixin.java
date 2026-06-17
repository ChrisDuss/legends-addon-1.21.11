package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.util.CustomNameplateState;
import legends.ultra.cool.addons.util.MultilineLabelRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMultilineLabelMixin<S extends EntityRenderState> {

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void legends$cancelVanillaLabelWhenCustomNameplateIsActive(
            S state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraRenderState,
            CallbackInfo ci
    ) {
        if (((CustomNameplateState) state).legends$getCustomNameplate() != null) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void legends$renderCustomNameplate(
            S state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraRenderState,
            CallbackInfo ci
    ) {
        CustomNameplateState customState = (CustomNameplateState) state;
        MultilineLabelRenderer.render(
                customState.legends$getCustomNameText(),
                customState.legends$getCustomStatsText(),
                customState.legends$getCustomNameplatePos(),
                customState.legends$getHealth(),
                customState.legends$getMaxHealth(),
                matrices,
                queue,
                state.light
        );
    }
}
