package legends.ultra.cool.addons;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.events.ClientTickHandler;
import legends.ultra.cool.addons.events.WorldJoinLeaveHandler;
import legends.ultra.cool.addons.hud.HudManager;
import legends.ultra.cool.addons.hud.HudRenderer;
import legends.ultra.cool.addons.hud.HudWidget;
import legends.ultra.cool.addons.hud.widget.*;
import legends.ultra.cool.addons.hud.widget.otherTypes.NameplateWidget;
import legends.ultra.cool.addons.hud.widget.otherTypes.UIToggle;
import legends.ultra.cool.addons.hud.widget.otherTypes.VaultBrowserWidget;
import legends.ultra.cool.addons.input.Keybinds;
import legends.ultra.cool.addons.overlay.ContainerOverlay;
import legends.ultra.cool.addons.resource.ServerResourcePackCache;
import legends.ultra.cool.addons.storage.VaultStorageManager;
import legends.ultra.cool.addons.util.ClientRenderCache;
import legends.ultra.cool.addons.update.UpdateManager;
// import legends.ultra.cool.addons.storage.WardrobeManager;
import net.fabricmc.api.ClientModInitializer;


public class LegendsAddonClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        //load config
        WidgetConfigManager.load();



        HudRenderer.init();
        Keybinds.init();
        WorldJoinLeaveHandler.init();
        ClientTickHandler.init();
        ContainerOverlay.init();
        VaultStorageManager.init();
        UpdateManager.init();
        ServerResourcePackCache.init();
        ClientRenderCache.init();

        TextWidget textWidget = new TextWidget(10, 10);
        CounterWidget counterWidget = new CounterWidget(10, 30);
        TimerWidget timerWidget = new TimerWidget(10, 50);
        NameplateWidget nameplateWidget = new NameplateWidget();
        UIToggle uiToggle = new UIToggle();
        Health health = new Health("Health Display", 10, 60);
        Mana mana = new Mana("Mana Display", 10, 70);
        Defense defense = new Defense("Defense Display", 10, 80);
        CooldownDisplay cooldownDisplay = new CooldownDisplay(10, 90);
        VaultBrowserWidget vaultBrowserWidget = new VaultBrowserWidget();
        ItemPickupTracker itemPickupTracker = new ItemPickupTracker(10, 100);
        MobKillTracker mobKillTracker = new MobKillTracker(10, 120);

//        addWidget(textWidget);
//        addWidget(counterWidget);
        addWidget(timerWidget);
        addWidget(nameplateWidget);
        addWidget(uiToggle);
        addWidget(health);
        addWidget(mana);
        addWidget(defense);
        addWidget(cooldownDisplay);
        addWidget(vaultBrowserWidget);
        addWidget(itemPickupTracker);
        addWidget(mobKillTracker);
    }

    public void addWidget(HudWidget w) {
        WidgetConfigManager.registerWidget(w);
        HudManager.register(w);
    }
}
