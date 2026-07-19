package legends.ultra.cool.addons.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import legends.ultra.cool.addons.LegendsAddon;
import legends.ultra.cool.addons.data.AddonConfigPaths;
import legends.ultra.cool.addons.mixin.client.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ItemDebugDump {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "legendsaddon_item_dump.json";
    private static final Type LIST_TYPE = new TypeToken<List<ItemDumpEntry>>() {}.getType();

    private ItemDebugDump() {
    }

    public static void dumpHoveredItem(MinecraftClient client) {
        if (client == null) {
            return;
        }

        ItemStack stack = getHoveredStack(client);
        if (stack == null || stack.isEmpty()) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("[LegendsAddon] No hovered item."), false);
            }
            return;
        }

        ItemDumpEntry entry = new ItemDumpEntry();
        entry.name = stack.getName().getString();
        entry.nbt = encodeStack(stack, client);
        entry.key = slugify(entry.name);

        Path path = getDumpPath(client);
        List<ItemDumpEntry> entries = readEntries(path);
        upsertEntry(entries, entry);
        writeEntries(path, entries);

        if (client.player != null) {
            client.player.sendMessage(Text.literal("[LegendsAddon] Dumped hovered item to " + path.getFileName()), false);
        }
    }

    private static ItemStack getHoveredStack(MinecraftClient client) {
        if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            return ItemStack.EMPTY;
        }

        Slot slot = ((HandledScreenAccessor) handledScreen).legends$getFocusedSlot();
        if (slot == null) {
            return ItemStack.EMPTY;
        }

        return slot.getStack();
    }

    private static String encodeStack(ItemStack stack, MinecraftClient client) {
        if (client.world == null) {
            return "{}";
        }

        RegistryWrapper.WrapperLookup registries = client.world.getRegistryManager();
        DynamicOps<NbtElement> ops = registries.getOps(NbtOps.INSTANCE);
        DataResult<NbtElement> result = ItemStack.CODEC.encodeStart(ops, stack);
        return result.resultOrPartial(error -> LegendsAddon.LOGGER.warn("[LegendsAddon] Item NBT encode failed: {}", error))
                .map(NbtElement::toString)
                .orElse("{}");
    }

    private static Path getDumpPath(MinecraftClient client) {
        return AddonConfigPaths.configFile(client, FILE_NAME);
    }

    private static List<ItemDumpEntry> readEntries(Path path) {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            List<ItemDumpEntry> entries = GSON.fromJson(reader, LIST_TYPE);
            return entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to read item dump file.", e);
            return new ArrayList<>();
        }
    }

    private static void writeEntries(Path path, List<ItemDumpEntry> entries) {
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to create config directory.", e);
        }

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(entries, LIST_TYPE, writer);
        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to write item dump file.", e);
        }
    }

    private static void upsertEntry(List<ItemDumpEntry> entries, ItemDumpEntry entry) {
        String target = normalizeName(entry.name);
        for (int i = 0; i < entries.size(); i++) {
            ItemDumpEntry existing = entries.get(i);
            if (existing == null) {
                continue;
            }
            if (normalizeName(existing.name).equals(target)) {
                if (existing.key != null && !existing.key.isBlank()) {
                    entry.key = existing.key;
                }
                entries.set(i, entry);
                return;
            }
        }
        entries.add(entry);
    }

    private static String normalizeName(String value) {
        return slugify(value);
    }

    private static String slugify(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.trim().toLowerCase(java.util.Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        boolean underscore = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
                underscore = false;
            } else if (!underscore && sb.length() > 0) {
                sb.append('_');
                underscore = true;
            }
        }
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == '_') {
            sb.setLength(len - 1);
        }
        return sb.toString();
    }

    private static final class ItemDumpEntry {
        public String key;
        public String name;
        public String nbt;
    }
}
