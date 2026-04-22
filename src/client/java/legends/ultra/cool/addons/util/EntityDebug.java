package legends.ultra.cool.addons.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Identifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EntityDebug {
    private static final Pattern HEALTH_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*/\\s*(\\d+(?:[.,]\\d+)?)");

    public static String dumpTargetFiltered(LivingEntity e) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return null;
        if (!(e instanceof LivingEntity mob)) {
            client.player.sendMessage(Text.literal("[LegendsAddon] Target is not a LivingEntity"), false);
            return null;
        }

        String mobName = mob.getDisplayName().getString();
        double[] mobStats = getMobStats(mob);
        double maxHp = mobStats[1];
        double itemDef = mobStats[2];
        double itemDmg = mobStats[3];

        return "\nMob: " + mobName + "\nMaxHP: " + maxHp + "\ndef: " + itemDef + "\ndmg: " + itemDmg;
    }

    public static double[] getMobStats(LivingEntity e) {
        ItemStack main = e.getMainHandStack();
        String mobName = e.getDisplayName().getString();

        double maxHp = 0;
        double currentHp = 0;
        int itemDef = readCustomInt(main, "def");
        int itemDmg = readCustomInt(main, "dmg");

        Matcher matcher = HEALTH_PATTERN.matcher(mobName);
        if (matcher.find()) {
            currentHp = parseNumber(matcher.group(1));
            maxHp = parseNumber(matcher.group(2));
        }

        return new double[]{currentHp, maxHp, itemDef, itemDmg};
    }

    private static int readCustomInt(ItemStack stack, String key) {
        if (stack.isEmpty()) return 0;

        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null || custom.isEmpty()) return 0;

        NbtCompound nbt = custom.copyNbt();
        return nbt.getInt(key).orElse(0);
    }

    private static double parseNumber(String value) {
        return Double.parseDouble(value.replace(',', '.'));
    }

    public static NbtCompound getEntityFullNbt(LivingEntity entity) {
        if (entity == null) {
            return new NbtCompound();
        }
        try {
            NbtWriteView writeView = NbtWriteView.create(ErrorReporter.EMPTY);
            entity.writeData(writeView);
            NbtCompound nbt = writeView.getNbt();
            if (!nbt.contains("id")) {
                Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
                if (id != null) {
                    nbt.putString("id", id.toString());
                }
            }
            return nbt;
        } catch (Exception e) {
            return new NbtCompound();
        }
    }
}
