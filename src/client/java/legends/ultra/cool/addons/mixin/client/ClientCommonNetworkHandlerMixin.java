package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.resource.ServerResourcePackCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.listener.ClientCommonPacketListener;
import net.minecraft.network.packet.s2c.common.ResourcePackRemoveS2CPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerMixin {
    @Shadow
    @Final
    protected MinecraftClient client;

    @Shadow
    @Final
    protected ClientConnection connection;

    @Inject(method = "onResourcePackSend", at = @At("HEAD"), cancellable = true)
    private void legends$loadCachedServerResourcePack(ResourcePackSendS2CPacket packet, CallbackInfo ci) {
        ServerResourcePackCache.onPackRequested(packet.id());

        NetworkThreadUtils.forceMainThread(
                packet,
                (ClientCommonPacketListener) (Object) this,
                client.getPacketApplyBatcher()
        );

        if (ServerResourcePackCache.tryHandle(packet, client, connection)) {
            ci.cancel();
        }
    }

    @Inject(method = "onResourcePackRemove", at = @At("HEAD"))
    private void legends$beginServerRequestedResourcePackRemoval(ResourcePackRemoveS2CPacket packet, CallbackInfo ci) {
        ServerResourcePackCache.beginServerRequestedRemoval();
    }

    @Inject(method = "onResourcePackRemove", at = @At("RETURN"))
    private void legends$endServerRequestedResourcePackRemoval(ResourcePackRemoveS2CPacket packet, CallbackInfo ci) {
        ServerResourcePackCache.endServerRequestedRemoval();
    }
}
