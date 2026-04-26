package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.widget.otherTypes.NameplateWidget;
import legends.ultra.cool.addons.hud.widget.otherTypes.NpcChatWidget;
import legends.ultra.cool.addons.render.DialogueRenderStateExt;
import legends.ultra.cool.addons.util.EntityDebug;
import legends.ultra.cool.addons.util.TextHeathbar;
import legends.ultra.cool.addons.util.UiVisibility;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.MannequinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererDisplayNameMixin<T extends Entity, S extends EntityRenderState> {

    private static final int UPDATE_EVERY_TICKS = 10;

    private static final int CLEANUP_EVERY_TICKS = 100;
    private static final int DROP_IF_NOT_USED_FOR_TICKS = 200;

    private static final Map<Integer, Text> TEXT_CACHE = new HashMap<>();
    private static final Map<Integer, Integer> NEXT_UPDATE_TICK = new HashMap<>();
    private static final Map<Integer, Integer> LAST_USED_TICK = new HashMap<>();

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void legends$forceAndOverrideDisplayName(T entity, S state, float tickDelta, CallbackInfo ci) {
        if (UiVisibility.isHudHidden()) return;

        EntityRenderStateAccessor acc = (EntityRenderStateAccessor) (Object) state;
        DialogueRenderStateExt dialogue = (DialogueRenderStateExt) (Object) state;
        dialogue.legends$setNpcDialogue(null);

        Text npcDialogue = NpcChatWidget.getDialogueLabel(entity);
        if (npcDialogue != null) {
            ensureNameLabelPos(entity, tickDelta, acc);
            dialogue.legends$setNpcDialogue(npcDialogue);
            return;
        }

        float range = WidgetConfigManager.getFloat("Nameplates", "range", 50f);
        double maxSq = (double) range * (double) range;
        if (state.squaredDistanceToCamera >= maxSq) return;

        if (!NameplateWidget.isEnabledGlobal()) return;
        if (!(entity instanceof LivingEntity living)) return;
        if (!living.isAlive()) return;
        if (entity instanceof PlayerEntity) return;
        if (entity instanceof MannequinEntity) return;
        if (entity instanceof ArmorStandEntity) return;

        ensureNameLabelPos(entity, tickDelta, acc);

        int now = entity.age;
        int id = entity.getId();

        Integer next = NEXT_UPDATE_TICK.get(id);
        if (next != null && now < next) {
            Text cached = TEXT_CACHE.get(id);
            if (cached != null) {
                LAST_USED_TICK.put(id, now);
                acc.legends$setDisplayName(cached);

                if ((now % CLEANUP_EVERY_TICKS) == 0) cleanupCache(now);
                return;
            }
        }

        String mobName = entity.getDisplayName().getString();

        int bracket = mobName.indexOf('[');
        if (bracket > 0) {
            mobName = mobName.substring(0, bracket).trim();
        }

        double[] stats = EntityDebug.getMobStats(living);

        Text custom = Text.literal(
                mobName
                        + "\n§c" + TextHeathbar.heathBar(stats[0], stats[1])
                        + "\n§c" + stats[0] + "❤ §r| §2" + stats[2] + "🛡 §r| §c" + stats[3] + "⚔"
        );

        TEXT_CACHE.put(id, custom);
        NEXT_UPDATE_TICK.put(id, now + UPDATE_EVERY_TICKS);
        LAST_USED_TICK.put(id, now);

        acc.legends$setDisplayName(custom);

        if ((now % CLEANUP_EVERY_TICKS) == 0) cleanupCache(now);
    }

    private static <T extends Entity> void ensureNameLabelPos(T entity, float tickDelta, EntityRenderStateAccessor acc) {
        if (acc.legends$getNameLabelPos() != null) {
            return;
        }

        Vec3d p = entity.getAttachments().getPointNullable(
                EntityAttachmentType.NAME_TAG,
                0,
                entity.getLerpedYaw(tickDelta)
        );
        acc.legends$setNameLabelPos(p);
    }

    private static void cleanupCache(int now) {
        Iterator<Map.Entry<Integer, Integer>> it = LAST_USED_TICK.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> e = it.next();
            int id = e.getKey();
            int last = e.getValue() == null ? 0 : e.getValue();

            if ((now - last) > DROP_IF_NOT_USED_FOR_TICKS) {
                it.remove();
                TEXT_CACHE.remove(id);
                NEXT_UPDATE_TICK.remove(id);
            }
        }
    }
}
