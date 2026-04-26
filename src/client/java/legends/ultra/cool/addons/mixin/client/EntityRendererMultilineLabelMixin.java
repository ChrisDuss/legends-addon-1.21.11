package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.render.DialogueRenderStateExt;
import legends.ultra.cool.addons.util.MultilineLabelRenderer;
import legends.ultra.cool.addons.util.VanillaStackedLabelRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMultilineLabelMixin<S extends EntityRenderState> {

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void legends$renderMultiline(
            S state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraRenderState,
            CallbackInfo ci
    ) {
        DialogueRenderStateExt dialogue = (DialogueRenderStateExt) (Object) state;
        Text npcDialogue = dialogue.legends$getNpcDialogue();
        if (npcDialogue != null && state.nameLabelPos != null) {
            ci.cancel();
            matrices.push();

            if (state.displayName != null) {
                queue.submitLabel(
                        matrices,
                        state.nameLabelPos,
                        0,
                        state.displayName,
                        !state.sneaking,
                        state.light,
                        state.squaredDistanceToCamera,
                        cameraRenderState
                );
                matrices.translate(0.0F, VanillaStackedLabelRenderer.getLineStep(), 0.0F);
            }

            VanillaStackedLabelRenderer.render(
                    npcDialogue,
                    matrices,
                    queue,
                    state.nameLabelPos,
                    0,
                    !state.sneaking,
                    state.light,
                    state.squaredDistanceToCamera,
                    cameraRenderState
            );
            matrices.pop();
            return;
        }

        if (MultilineLabelRenderer.render(state.displayName, state.nameLabelPos, matrices, queue, state.light)) {
            ci.cancel();
        }
    }
}
