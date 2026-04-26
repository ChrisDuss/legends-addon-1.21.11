package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.render.DialogueRenderStateExt;
import legends.ultra.cool.addons.util.VanillaStackedLabelRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererDialogueMixin {

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void legends$renderMultilineNpcDialogue(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState, CallbackInfo ci) {
        DialogueRenderStateExt dialogue = (DialogueRenderStateExt) (Object) state;
        if (dialogue.legends$getNpcDialogue() == null || state.nameLabelPos == null) {
            return;
        }

        ci.cancel();
        matrices.push();

        int verticalOffset = state.extraEars ? -10 : 0;
        boolean visible = !state.sneaking;

        if (state.playerName != null) {
            queue.submitLabel(
                    matrices,
                    state.nameLabelPos,
                    verticalOffset,
                    state.playerName,
                    visible,
                    state.light,
                    state.squaredDistanceToCamera,
                    cameraRenderState
            );

            matrices.translate(0.0F, VanillaStackedLabelRenderer.getLineStep(), 0.0F);
        }

        VanillaStackedLabelRenderer.render(
                dialogue.legends$getNpcDialogue(),
                matrices,
                queue,
                state.nameLabelPos,
                verticalOffset,
                visible,
                state.light,
                state.squaredDistanceToCamera,
                cameraRenderState
        );
        matrices.pop();
    }
}
