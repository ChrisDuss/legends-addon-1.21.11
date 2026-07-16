package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.util.ContainerValueScanner;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenContainerValueDragMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void legends$startContainerValueDrag(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (ContainerValueScanner.mouseClicked((HandledScreen<?>) (Object) this, click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void legends$dragContainerValueLabel(
            Click click,
            double offsetX,
            double offsetY,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (ContainerValueScanner.mouseDragged((HandledScreen<?>) (Object) this, click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void legends$releaseContainerValueDrag(Click click, CallbackInfoReturnable<Boolean> cir) {
        if (ContainerValueScanner.mouseReleased()) {
            cir.setReturnValue(true);
        }
    }
}
