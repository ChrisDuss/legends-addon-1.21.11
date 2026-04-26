package legends.ultra.cool.addons.util;

import net.minecraft.client.MinecraftClient;

public final class UiVisibility {
    private UiVisibility() {
    }

    public static boolean isHudHidden() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.options != null && client.options.hudHidden;
    }
}
