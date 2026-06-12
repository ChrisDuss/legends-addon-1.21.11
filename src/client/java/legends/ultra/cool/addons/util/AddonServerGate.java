package legends.ultra.cool.addons.util;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;

public final class AddonServerGate {
    private static final String SETTINGS_CONFIG_ID = "__general_settings__";
    private static final String LEGENDS_ONLY_MODE_KEY = "legendsServerOnlyMode";
    public static final String LEGENDS_SERVER_HOST = "legendsrpg.minehut.gg";
    private static final boolean DEFAULT_LEGENDS_ONLY_MODE = false;

    private AddonServerGate() {
    }

    public static boolean isLegendsOnlyModeEnabled() {
        return WidgetConfigManager.getBool(
                SETTINGS_CONFIG_ID,
                LEGENDS_ONLY_MODE_KEY,
                DEFAULT_LEGENDS_ONLY_MODE
        );
    }

    public static void setLegendsOnlyModeEnabled(boolean enabled) {
        WidgetConfigManager.setBool(SETTINGS_CONFIG_ID, LEGENDS_ONLY_MODE_KEY, enabled, true);
    }

    public static boolean shouldRunOnCurrentServer() {
        return !isLegendsOnlyModeEnabled() || isOnLegendsServer();
    }

    public static boolean isOnLegendsServer() {
        return isLegendsServerAddress(getCurrentServerAddress());
    }

    public static String getCurrentServerAddress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.isInSingleplayer()) {
            return "";
        }

        var entry = client.getCurrentServerEntry();
        if (entry == null || entry.address == null) {
            if (client.getNetworkHandler() != null
                    && client.getNetworkHandler().getConnection() != null
                    && client.getNetworkHandler().getConnection().getAddress() != null) {
                return normalizeServerAddress(client.getNetworkHandler().getConnection().getAddress().toString());
            }
            return "";
        }

        return normalizeServerAddress(entry.address);
    }

    public static boolean isLegendsServerAddress(String address) {
        return LEGENDS_SERVER_HOST.equals(normalizeServerAddress(address));
    }

    private static String normalizeServerAddress(String address) {
        if (address == null) {
            return "";
        }

        String normalized = address.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }

        int slashIndex = normalized.indexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(0, slashIndex);
        }

        int colonIndex = normalized.indexOf(':');
        if (colonIndex >= 0) {
            normalized = normalized.substring(0, colonIndex);
        }

        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}
