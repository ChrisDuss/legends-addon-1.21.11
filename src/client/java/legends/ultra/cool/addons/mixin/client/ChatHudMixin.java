package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.hud.widget.otherTypes.NpcChatWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;

    @Shadow
    public abstract boolean isChatFocused();

    @Unique
    private int legends$npcVisibleLinesToHide;

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD")
    )
    private void legends$captureNpcMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        legends$npcVisibleLinesToHide = 0;

        if (!NpcChatWidget.isEnabledGlobal()) return;
        if (!NpcChatWidget.isNpcMessage(message)) return;

        boolean routedToNpc = NpcChatWidget.captureNpcMessage(message);
        if (!routedToNpc) {
            return;
        }

        if (isChatFocused()) {
            return;
        }

        ChatHudAccessor accessor = (ChatHudAccessor) this;
        int chatWidth = MathHelper.floor(accessor.legends$getWidth() / accessor.legends$getChatScale());
        ChatHudLine line = new ChatHudLine(client.inGameHud.getTicks(), message, signatureData, indicator);
        legends$npcVisibleLinesToHide = line.breakLines(client.textRenderer, chatWidth).size();
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("RETURN")
    )
    private void legends$hideNpcLiveMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        if (legends$npcVisibleLinesToHide <= 0) {
            return;
        }

        for (int i = 0; i < legends$npcVisibleLinesToHide && !visibleMessages.isEmpty(); i++) {
            visibleMessages.removeFirst();
        }

        legends$npcVisibleLinesToHide = 0;
    }

    @Inject(method = "setClientScreen", at = @At("RETURN"))
    private void legends$refreshNpcChatOnOpen(ChatHud.ChatMethod chatMethod, ChatScreen.Factory<?> factory, CallbackInfo ci) {
        ((ChatHudAccessor) this).legends$refresh();
    }
}
