package legends.ultra.cool.addons.mixin.client;

import legends.ultra.cool.addons.util.CustomNameplateState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateCustomNameplateMixin implements CustomNameplateState {
    @Unique
    private Text legends$customNameplate;

    @Unique
    private Vec3d legends$customNameplatePos;

    @Unique
    private Text legends$customNameText;

    @Unique
    private Text legends$customStatsText;

    @Unique
    private double legends$Health;

    @Unique
    private double legends$MaxHealth;

    @Override
    public Text legends$getCustomNameplate() {
        return this.legends$customNameplate;
    }

    @Override
    public void legends$setCustomNameplate(Text text) {
        this.legends$customNameplate = text;
    }

    @Override
    public Vec3d legends$getCustomNameplatePos() {
        return this.legends$customNameplatePos;
    }

    @Override
    public void legends$setCustomNameplatePos(Vec3d pos) {
        this.legends$customNameplatePos = pos;
    }

    @Override
    public Text legends$getCustomNameText() {
        return this.legends$customNameText;
    }

    @Override
    public void legends$setCustomNameText(Text text) {
        this.legends$customNameText = text;
    }

    @Override
    public Text legends$getCustomStatsText() {
        return this.legends$customStatsText;
    }

    @Override
    public void legends$setCustomStatsText(Text text) {
        this.legends$customStatsText = text;
    }

    @Override
    public double legends$getHealth() {
        return this.legends$Health;
    }

    @Override
    public void legends$setHealth(double value) {
        this.legends$Health = value;
    }

    @Override
    public double legends$getMaxHealth() {
        return this.legends$MaxHealth;
    }

    @Override
    public void legends$setMaxHealth(double value) {
        this.legends$MaxHealth = value;
    }
}
