package legends.ultra.cool.addons.events;

import legends.ultra.cool.addons.hud.HudManager;
import legends.ultra.cool.addons.hud.widget.CooldownDisplay;
import legends.ultra.cool.addons.hud.widget.CounterWidget;
import legends.ultra.cool.addons.hud.widget.TimerWidget;
import legends.ultra.cool.addons.overlay.ContainerOverlay;
import legends.ultra.cool.addons.storage.VaultStorageManager;
import legends.ultra.cool.addons.util.AddonServerGate;
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
            boolean addonEnabled = AddonServerGate.shouldRunOnCurrentServer();
            if (addonEnabled) {
                HudManager.getWidgets().forEach(widget -> {
                    if (widget instanceof CounterWidget counter && counter.isEnabled()) {
                        counter.tick();
                    }

                    if (widget instanceof CooldownDisplay c && c.isEnabled()) {
                        c.tick();
                    }

                    if (widget instanceof TimerWidget timer && timer.isEnabled()) {
                        timer.tick(timer.getToggleState());
                    }
                });
            }

            boolean isContainerOpen = client.currentScreen instanceof HandledScreen<?>;
            if (addonEnabled) {
                if (isContainerOpen) {
                    containerTicks++;
                } else {
                    containerTicks = 0;
                }

                if (isContainerOpen && (!wasOpen || containerTicks >= CONTAINER_SCAN_INTERVAL_TICKS)) {
                    ContainerOverlay.fTreeCheck();
                    ContainerOverlay.ryanContainer();
                    containerTicks = 0;
                }
            } else {
                containerTicks = 0;
            }

            VaultStorageManager.tick(client);
            wasOpen = isContainerOpen;
        });
    }

}

