package legends.ultra.cool.addons.util;

import legends.ultra.cool.addons.LegendsAddon;
import legends.ultra.cool.addons.data.WidgetConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class Jumpscare {
    private static final String CONFIG_ID = "__general_settings__";
    private static final String ENABLED_KEY = "foxyJumpscare";
    private static final Identifier SOUND_ID = Identifier.of(LegendsAddon.MOD_ID, "ui.sync");

    private static final int ODDS = 1_000_000;
    private static final int FRAME_COUNT = 26;
    private static final int FRAME_RATE = 30;
    private static final int FRAME_WIDTH = 640;
    private static final int FRAME_HEIGHT = 360;
    private static final long ROLL_INTERVAL_NANOS = 1_000_000_000L;
    private static final float SOUND_PITCH = 1.0f;
    private static final float SOUND_VOLUME = 1000.0f;
    private static final SoundCategory SOUND_CATEGORY = SoundCategory.MASTER;
    private static final Identifier[] FRAMES = createFrameIds();

    private static boolean initialized = false;
    private static long startedAtNanos = -1L;
    private static long nextRollAtNanos = 0L;

    private Jumpscare() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ClientTickEvents.END_CLIENT_TICK.register(Jumpscare::tick);
        HudRenderCallback.EVENT.register((context, tickDelta) -> render(context));
    }

    public static boolean isEnabled() {
        return WidgetConfigManager.getBool(CONFIG_ID, ENABLED_KEY, false);
    }

    public static void setEnabled(boolean enabled) {
        WidgetConfigManager.setBool(CONFIG_ID, ENABLED_KEY, enabled, true);
    }

    public static void trigger() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        startedAtNanos = System.nanoTime();
        client.getSoundManager().play(new PositionedSoundInstance(
                SOUND_ID,
                SOUND_CATEGORY,
                SOUND_VOLUME,
                SOUND_PITCH,
                SoundInstance.createRandom(),
                false,
                0,
                SoundInstance.AttenuationType.NONE,
                0.0,
                0.0,
                0.0,
                true
        ));
    }

    private static void tick(MinecraftClient client) {
        if (!shouldRoll(client)) {
            return;
        }

        long now = System.nanoTime();
        if (now < nextRollAtNanos) {
            return;
        }
        nextRollAtNanos = now + ROLL_INTERVAL_NANOS;

        if (ThreadLocalRandom.current().nextInt(ODDS) == 0) {
            trigger();
        }
    }

    private static boolean shouldRoll(MinecraftClient client) {
        return client != null
                && client.player != null
                && client.world != null
                && client.currentScreen == null
                && client.isWindowFocused()
                && AddonServerGate.shouldRunOnCurrentServer()
                && isEnabled()
                && !isActive();
    }

    private static boolean isActive() {
        return currentFrameIndex() < FRAME_COUNT;
    }

    private static void render(DrawContext context) {
        int frame = currentFrameIndex();
        if (frame >= FRAME_COUNT) {
            startedAtNanos = -1L;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        double scale = Math.max(screenWidth / (double) FRAME_WIDTH, screenHeight / (double) FRAME_HEIGHT);
        int drawWidth = (int) Math.ceil(FRAME_WIDTH * scale);
        int drawHeight = (int) Math.ceil(FRAME_HEIGHT * scale);
        int drawX = (screenWidth - drawWidth) / 2;
        int drawY = (screenHeight - drawHeight) / 2;

        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                FRAMES[frame],
                drawX,
                drawY,
                0f,
                0f,
                drawWidth,
                drawHeight,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                FRAME_WIDTH,
                FRAME_HEIGHT
        );
    }

    private static int currentFrameIndex() {
        if (startedAtNanos < 0L) {
            return FRAME_COUNT;
        }

        long elapsedNanos = System.nanoTime() - startedAtNanos;
        return (int) (elapsedNanos * FRAME_RATE / 1_000_000_000L);
    }

    private static Identifier[] createFrameIds() {
        Identifier[] ids = new Identifier[FRAME_COUNT];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = Identifier.of(
                    LegendsAddon.MOD_ID,
                    String.format(Locale.ROOT, "textures/gui/system_cache/layer_%03d.png", i + 1)
            );
        }
        return ids;
    }
}
