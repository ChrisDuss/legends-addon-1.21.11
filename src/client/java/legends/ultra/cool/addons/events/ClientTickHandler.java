package legends.ultra.cool.addons.events;

import legends.ultra.cool.addons.hud.HudManager;
import legends.ultra.cool.addons.hud.widget.CounterWidget;
import legends.ultra.cool.addons.hud.widget.TimerWidget;
import legends.ultra.cool.addons.overlay.ContainerOverlay;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;


public class ClientTickHandler {
    private static final int CONTAINER_SCAN_INTERVAL_TICKS = 20;
    private static boolean initialized = false;

    private static boolean wasOpen = false;
    private static int containerTicks = 0;

    public static void init() {
        if (initialized) return;
        initialized = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HudManager.getWidgets().forEach(widget -> {
                if (widget instanceof CounterWidget counter && counter.enabled) {
                    counter.tick();
                }

                if (widget instanceof TimerWidget timer && timer.enabled) {
                    timer.tick(timer.getToggleState());
                }
            });

            boolean isContainerOpen = client.currentScreen instanceof HandledScreen<?>;
            if (isContainerOpen) {
                containerTicks++;
            } else {
                containerTicks = 0;
            }

            if (isContainerOpen && (!wasOpen || containerTicks >= CONTAINER_SCAN_INTERVAL_TICKS)) {
                ContainerOverlay.fTreeCheck();
                containerTicks = 0;
            }

            wasOpen = isContainerOpen;
        });
    }

}

