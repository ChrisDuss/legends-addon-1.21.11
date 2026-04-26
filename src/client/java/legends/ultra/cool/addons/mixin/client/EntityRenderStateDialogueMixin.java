package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.render.DialogueRenderStateExt;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateDialogueMixin implements DialogueRenderStateExt {
    @Unique
    private Text legends$npcDialogue;

    @Override
    public Text legends$getNpcDialogue() {
        return legends$npcDialogue;
    }

    @Override
    public void legends$setNpcDialogue(Text text) {
        legends$npcDialogue = text;
    }
}
