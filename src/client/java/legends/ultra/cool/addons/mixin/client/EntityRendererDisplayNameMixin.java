package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.widget.otherTypes.NameplateWidget;
import legends.ultra.cool.addons.util.CustomNameplateState;
import legends.ultra.cool.addons.util.EntityDebug;
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
        CustomNameplateState customState = (CustomNameplateState) state;

        float range = WidgetConfigManager.getFloat("Nameplates", "range", 50f);
        double maxSq = (double) range * (double) range;
        if (state.squaredDistanceToCamera >= maxSq) return;

        if (!NameplateWidget.isEnabledGlobal()) return;
        if (!(entity instanceof LivingEntity living)) return;
        if (!living.isAlive()) return;
        if (entity instanceof PlayerEntity) return;
        if (entity instanceof MannequinEntity) return;
        if (entity instanceof ArmorStandEntity) return;

        customState.legends$setCustomNameplatePos(resolveNameLabelPos(entity, tickDelta, acc));

        int now = entity.age;
        int id = entity.getId();
        double[] stats = EntityDebug.getMobStats(living);
        customState.legends$setHealth(stats[0]);
        customState.legends$setMaxHealth(stats[1]);

        String mobName = entity.getDisplayName().getString();
        int bracket = mobName.indexOf('[');
        if (bracket > 0) {
            mobName = mobName.substring(0, bracket).trim();
        }

        customState.legends$setCustomNameText(Text.literal(mobName));
        customState.legends$setCustomStatsText(
                Text.literal("§c" + stats[0] + "\u2764 §r| §2" + stats[2] + "\ud83d\udee1 §r| §c" + stats[3] + "\u2694")
        );

        if (!NameplateWidget.shouldShowNameText()
                && !NameplateWidget.shouldShowHealthBar()
                && !NameplateWidget.shouldShowStatsText()) {
            customState.legends$setCustomNameplate(null);
            acc.legends$setDisplayName(null);
            return;
        }

        Integer next = NEXT_UPDATE_TICK.get(id);
        if (next != null && now < next) {
            Text cached = TEXT_CACHE.get(id);
            if (cached != null) {
                LAST_USED_TICK.put(id, now);
                customState.legends$setCustomNameplate(cached);
                acc.legends$setDisplayName(null);

                if ((now % CLEANUP_EVERY_TICKS) == 0) cleanupCache(now);
                return;
            }
        }

        Text custom = Text.empty();

        TEXT_CACHE.put(id, custom);
        NEXT_UPDATE_TICK.put(id, now + UPDATE_EVERY_TICKS);
        LAST_USED_TICK.put(id, now);

        customState.legends$setCustomNameplate(custom);
        acc.legends$setDisplayName(null);

        if ((now % CLEANUP_EVERY_TICKS) == 0) cleanupCache(now);
    }

    private static <T extends Entity> Vec3d resolveNameLabelPos(T entity, float tickDelta, EntityRenderStateAccessor acc) {
        if (acc.legends$getNameLabelPos() != null) {
            return acc.legends$getNameLabelPos();
        }

        return entity.getAttachments().getPointNullable(
                EntityAttachmentType.NAME_TAG,
                0,
                entity.getLerpedYaw(tickDelta)
        );
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
