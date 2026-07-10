package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.resource.ServerResourcePackCache;
import net.minecraft.client.resource.server.ServerResourcePackLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerResourcePackLoader.class)
public abstract class ServerResourcePackLoaderMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void legends$onServerResourcePackRemoved(UUID id, CallbackInfo ci) {
        ServerResourcePackCache.onPackRemoved(id);
    }

    @Inject(method = "removeAll", at = @At("HEAD"))
    private void legends$onAllServerResourcePacksRemoved(CallbackInfo ci) {
        ServerResourcePackCache.onAllPacksRemoved();
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void legends$onServerResourcePackLoaderCleared(CallbackInfo ci) {
        if (ServerResourcePackCache.shouldPreserveOnClientDisconnect()) {
            ci.cancel();
            return;
        }

        ServerResourcePackCache.onAllPacksRemoved();
    }
}
