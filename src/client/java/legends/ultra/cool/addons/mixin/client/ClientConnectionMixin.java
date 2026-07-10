package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.resource.ServerResourcePackCache;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"))
    private void legends$captureResourcePackStatus(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ResourcePackStatusC2SPacket statusPacket) {
            ServerResourcePackCache.onStatusSent(statusPacket);
        }
    }
}
