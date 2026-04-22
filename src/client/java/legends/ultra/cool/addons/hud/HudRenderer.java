package legends.ultra.cool.addons.hud;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

public class HudRenderer {
    private static boolean initialized = false;
    private static int lastScaledWidth = -1;
    private static int lastScaledHeight = -1;


    public static void init() {
        if (initialized) return;
        initialized = true;

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getWindow() != null) {
                int scaledWidth = client.getWindow().getScaledWidth();
                int scaledHeight = client.getWindow().getScaledHeight();

                if (lastScaledWidth <= 0 || lastScaledHeight <= 0) {
                    HudManager.getWidgets().forEach(WidgetConfigManager::applyWidgetPosition);
                }

                if (lastScaledWidth > 0 && lastScaledHeight > 0
                        && (scaledWidth != lastScaledWidth || scaledHeight != lastScaledHeight)) {
                    HudManager.getWidgets().forEach(w -> {
                        WidgetConfigManager.applyWidgetPosition(w);
                        w.onScreenSizeChanged(lastScaledWidth, lastScaledHeight, scaledWidth, scaledHeight);
                    });
                }

                lastScaledWidth = scaledWidth;
                lastScaledHeight = scaledHeight;
            }

            HudManager.getWidgets().forEach(w -> {
                if (w.enabled) w.render(context);
            });
        });
    }
}
