package legends.ultra.cool.addons.input;

import legends.ultra.cool.addons.hud.HudEditorScreen;
import legends.ultra.cool.addons.hud.HudManager;
import legends.ultra.cool.addons.hud.widget.TimerWidget;
import legends.ultra.cool.addons.overlay.ContainerOverlay;
import legends.ultra.cool.addons.util.EntityDebug;
import legends.ultra.cool.addons.util.ItemDebugDump;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import  net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class Keybinds {
    public static KeyBinding OPEN_EDITOR;
    public static KeyBinding TOGGLE_TIMER;
    public static KeyBinding RESET_TIMER;
    public static final KeyBinding.Category MAIN_CATEGORY = KeyBinding.Category.create(Identifier.of("legends_addon"));

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


        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            //OPEN EDITOR
            while (OPEN_EDITOR.wasPressed()) {
                client.setScreen(new HudEditorScreen());
            }

            //TIMER
            HudManager.getWidgets().forEach(widget -> {
                if (widget instanceof TimerWidget timer && timer.enabled) {
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

