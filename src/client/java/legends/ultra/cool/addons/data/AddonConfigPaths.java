package legends.ultra.cool.addons.data;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class AddonConfigPaths {
    private static final String CONFIG_DIR = "config";
    private static final String ADDON_CONFIG_DIR = "legends-addon";
    private static final List<String> KNOWN_LEGACY_FILES = List.of(
            "legendsaddon_widgets.json",
            "legendsaddon_vault_names.json",
            "legendsaddon_vault_settings.json",
            "legendsaddon_vault_snapshots.json",
            "legendsaddon_wardrobe_sets.json",
            "legendsaddon_item_dump.json",
            "legends-addon-server-resource-pack.properties"
    );

    private AddonConfigPaths() {
    }

    public static Path configFile(String fileName) {
        return configFile(MinecraftClient.getInstance(), fileName);
    }

    public static Path configFile(MinecraftClient client, String fileName) {
        Path newPath = configDirectory(client).resolve(fileName);
        migrateLegacyFile(client, fileName, newPath);
        return newPath;
    }

    public static void migrateKnownLegacyFiles() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (String fileName : KNOWN_LEGACY_FILES) {
            configFile(client, fileName);
        }
    }

    private static Path configDirectory(MinecraftClient client) {
        return rootConfigDirectory(client).resolve(ADDON_CONFIG_DIR);
    }

    private static Path rootConfigDirectory(MinecraftClient client) {
        return client.runDirectory.toPath().resolve(CONFIG_DIR);
    }

    private static void migrateLegacyFile(MinecraftClient client, String fileName, Path newPath) {
        Path legacyPath = rootConfigDirectory(client).resolve(fileName);
        if (!Files.isRegularFile(legacyPath) || Files.exists(newPath)) {
            return;
        }

        try {
            Files.createDirectories(newPath.getParent());
            Files.move(legacyPath, newPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }
}
