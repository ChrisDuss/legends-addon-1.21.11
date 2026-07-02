package legends.ultra.cool.addons.input;

import legends.ultra.cool.addons.LegendsAddon;
import legends.ultra.cool.addons.hud.HudEditorScreen;
import legends.ultra.cool.addons.hud.HudManager;
import legends.ultra.cool.addons.hud.widget.TimerWidget;
import legends.ultra.cool.addons.storage.VaultStorageManager;
import legends.ultra.cool.addons.util.AddonServerGate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class Keybinds {
    public static KeyBinding OPEN_EDITOR;
    public static KeyBinding TOGGLE_TIMER;
    public static KeyBinding RESET_TIMER;
    public static KeyBinding OPEN_VAULT;
    public static KeyBinding OPEN_WARDROBE;
    public static KeyBinding OPEN_BESTIARY;
    public static final KeyBinding.Category MAIN_CATEGORY = KeyBinding.Category.create(Identifier.of(LegendsAddon.MOD_ID, "main"));

    public static void init() {
        OPEN_EDITOR = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "Toggle Editor",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_RIGHT_SHIFT,
                        MAIN_CATEGORY
                )
        );

        TOGGLE_TIMER = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "Toggle Timer",
                        GLFW.GLFW_KEY_X,
                        MAIN_CATEGORY
                ));

        RESET_TIMER = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "Reset Timer",
                        GLFW.GLFW_KEY_C,
                        MAIN_CATEGORY
                ));

        OPEN_VAULT = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "Open Vault",
                        GLFW.GLFW_KEY_V,
                        MAIN_CATEGORY
                ));

        OPEN_WARDROBE = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "Open Wardrobe",
                        GLFW.GLFW_KEY_G,
                        MAIN_CATEGORY
                ));

        OPEN_BESTIARY = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "Open Bestiary",
                        GLFW.GLFW_ANGLE_PLATFORM_TYPE_NONE,
                        MAIN_CATEGORY
                ));


        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            //OPEN EDITOR
            while (OPEN_EDITOR.wasPressed()) {
                client.setScreen(new HudEditorScreen());
            }

            if (!AddonServerGate.shouldRunOnCurrentServer()) {
                return;
            }

            while (OPEN_VAULT.wasPressed()) {
                VaultStorageManager.openStorageMenu(client);
            }

            while (OPEN_WARDROBE.wasPressed()) {
                if (client.currentScreen == null && client.getNetworkHandler() != null) {
                    client.getNetworkHandler().sendChatCommand("wardrobe");
                }
            }

            while (OPEN_BESTIARY.wasPressed()) {
                if (client.currentScreen == null && client.getNetworkHandler() != null) {
                    client.getNetworkHandler().sendChatCommand("bestiary");
                }
            }

            //TIMER
            HudManager.getWidgets().forEach(widget -> {
                if (widget instanceof TimerWidget timer && timer.isEnabled()) {
                    while (TOGGLE_TIMER.wasPressed()) {
                        timer.toggleTick();
                    }

                    while (RESET_TIMER.wasPressed()) {
                        timer.reset();
                    }
                }
            });
        });
    }
}
