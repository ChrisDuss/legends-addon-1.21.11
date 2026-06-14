package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.hud.widget.CooldownDisplay;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD")
    )
    private void legends$captureCooldownMessage(
            Text message,
            MessageSignatureData signatureData,
            MessageIndicator indicator,
            CallbackInfo ci
    ) {
        boolean serverMessage = Objects.equals(MessageIndicator.system(), indicator)
                || Objects.equals(MessageIndicator.singlePlayer(), indicator);
        CooldownDisplay.captureChatMessage(message, serverMessage);
    }
}
