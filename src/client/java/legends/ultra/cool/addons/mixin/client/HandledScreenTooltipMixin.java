package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.storage.VaultStorageManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenTooltipMixin {
    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void legends$suppressTooltipWhenVaultBrowserOverlayIsOpen(
            DrawContext context,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        if (VaultStorageManager.hasBrowserOverlay((HandledScreen<?>) (Object) this)) {
            ci.cancel();
        }
    }
}
