package legends.ultra.cool.addons.events;

import legends.ultra.cool.addons.hud.HudManager;
import legends.ultra.cool.addons.hud.widget.CounterWidget;
import legends.ultra.cool.addons.hud.widget.ItemPickupTracker;
import legends.ultra.cool.addons.hud.widget.MobKillTracker;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class WorldJoinLeaveHandler {

    public static void init() {

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // World left
            ItemPickupTracker.reset();
            MobKillTracker.clearTransientTracking();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // World joined
            ItemPickupTracker.reset();
            MobKillTracker.clearTransientTracking();
            HudManager.getWidgets().forEach(widget -> {
                if (widget instanceof CounterWidget counter) {
                    counter.reset();
                }
            });
        });
    }
}
