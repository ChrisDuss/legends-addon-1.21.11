package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.LegendsAddon;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SoundSystem.class)
public class SystemAudioMixin {
    private static final Identifier TARGET_SOUND_ID = Identifier.of(LegendsAddon.MOD_ID, "ui.sync");
    private static final float TARGET_GAIN = 8.0f;

    @Shadow
    @Final
    private GameOptions options;

    @Shadow
    @Final
    private Object2FloatMap<SoundCategory> volumes;

    @Redirect(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F"
            )
    )
    private float legends$boostTargetSound(SoundSystem soundSystem, float volume, SoundCategory category, SoundInstance soundInstance) {
        if (TARGET_SOUND_ID.equals(soundInstance.getId())) {
            return TARGET_GAIN * MathHelper.clamp(options.getSoundVolume(SoundCategory.MASTER), 0.0f, 1.0f) * volumes.getFloat(SoundCategory.MASTER);
        }

        return MathHelper.clamp(volume, 0.0f, 1.0f) * MathHelper.clamp(options.getSoundVolume(category), 0.0f, 1.0f) * volumes.getFloat(category);
    }
}
