package legends.ultra.cool.addons.util;

import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public interface CustomNameplateState {
    Text legends$getCustomNameplate();
    void legends$setCustomNameplate(Text text);

    Vec3d legends$getCustomNameplatePos();
    void legends$setCustomNameplatePos(Vec3d pos);

    Text legends$getCustomNameText();
    void legends$setCustomNameText(Text text);

    Text legends$getCustomStatsText();
    void legends$setCustomStatsText(Text text);

    double legends$getHealth();
    void legends$setHealth(double value);

    double legends$getMaxHealth();
    void legends$setMaxHealth(double value);
}
