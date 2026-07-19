package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.resource.ServerResourcePackCache;
import net.minecraft.client.resource.server.PackStateChangeCallback;
import net.minecraft.client.resource.server.ServerResourcePackLoader;
import net.minecraft.client.resource.server.ServerResourcePackManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerResourcePackLoader.class)
public abstract class ServerResourcePackLoaderMixin {
    @Shadow
    @Final
    private static PackStateChangeCallback DEBUG_PACK_STATE_CHANGE_CALLBACK;

    @Shadow
    PackStateChangeCallback packStateChangeCallback;

    @Shadow
    @Final
    ServerResourcePackManager manager;

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
            ServerResourcePackCache.packsToRemoveOnClientDisconnect().forEach(id ->
                    ((ServerResourcePackLoader) (Object) this).remove(id)
            );
            packStateChangeCallback = DEBUG_PACK_STATE_CHANGE_CALLBACK;
            manager.resetAcceptanceStatus();
            ci.cancel();
            return;
        }

        ServerResourcePackCache.onAllPacksRemoved();
    }
}
