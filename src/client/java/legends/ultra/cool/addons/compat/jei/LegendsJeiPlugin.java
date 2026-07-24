package legends.ultra.cool.addons.compat.jei;

import legends.ultra.cool.addons.storage.VaultBrowserScreen;
import legends.ultra.cool.addons.storage.VaultRenameScreen;
import legends.ultra.cool.addons.storage.VaultStorageManager;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.util.Identifier;

import java.util.List;

@JeiPlugin
public final class LegendsJeiPlugin implements IModPlugin {
    private static final Identifier PLUGIN_ID = Identifier.of("legends-addon", "vault_browser");

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(VaultBrowserScreen.class, LegendsJeiPlugin::fullScreenProperties);
        registration.addGuiScreenHandler(VaultRenameScreen.class, LegendsJeiPlugin::fullScreenProperties);
        registration.addGlobalGuiHandler(new VaultScreenExclusionHandler());
    }

    private static IGuiProperties fullScreenProperties(Screen screen) {
        return new FullScreenGuiProperties(screen);
    }

    private record FullScreenGuiProperties(Screen screen) implements IGuiProperties {
        @Override
        public Class<? extends Screen> screenClass() {
            return screen.getClass();
        }

        @Override
        public int guiLeft() {
            return 0;
        }

        @Override
        public int guiTop() {
            return 0;
        }

        @Override
        public int guiXSize() {
            return screen.width;
        }

        @Override
        public int guiYSize() {
            return screen.height;
        }

        @Override
        public int screenWidth() {
            return screen.width;
        }

        @Override
        public int screenHeight() {
            return screen.height;
        }
    }

    private static final class VaultScreenExclusionHandler implements IGlobalGuiHandler {
        @Override
        public List<Rect2i> getGuiExtraAreas() {
            Screen screen = MinecraftClient.getInstance().currentScreen;
            if (screen == null || !VaultStorageManager.shouldHideJeiOverlay(screen)) {
                return List.of();
            }

            return List.of(new Rect2i(0, 0, screen.width, screen.height));
        }
    }
}
