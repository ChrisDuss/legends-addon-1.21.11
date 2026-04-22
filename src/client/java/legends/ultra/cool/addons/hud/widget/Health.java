package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudWidget;
import legends.ultra.cool.addons.mixin.client.InGameHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Health extends HudWidget {
    private static final Pattern HEALTH_PATTERN = Pattern.compile("♥\\s*(\\d+(?:[.,]\\d+)?)\\s*/\\s*(\\d+(?:[.,]\\d+)?)\\s*♥|(?<!\\d)(\\d+(?:[.,]\\d+)?)\\s*/\\s*(\\d+(?:[.,]\\d+)?)(?!\\d)");
    private static final float DEFAULT_BAR_Y_OFFSET = 13f;

    private static Health INSTANCE;

    private String health = "Null";
    private double maxHealth = 0;
    private double currentHealth = 0;
    private double cachedTextWidth = 0;
    private int cachedTextHeight = 0;

    private final Bar hpBar;

    public static boolean barToggle = false;

    public Health(String name, int x, int y) {
        super(name, x, y);
        this.hpBar = new Bar(name, "HealthBar", x, y + DEFAULT_BAR_Y_OFFSET, currentHealth, maxHealth);
        INSTANCE = this;
    }

    public static boolean isEnabledGlobal() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    public static boolean shouldHideOverlay(Text overlay) {
        return isEnabledGlobal() && overlay != null && HEALTH_PATTERN.matcher(overlay.getString()).find();
    }

    public static Text stripHealthOverlay(Text overlay) {
        if (!shouldHideOverlay(overlay)) {
            return overlay;
        }

        String stripped = HEALTH_PATTERN.matcher(overlay.getString()).replaceAll("").trim();
        return stripped.isEmpty() ? Text.empty() : Text.literal(stripped);
    }

    @Override
    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        final String w = getName();
        boolean bgToggle = WidgetConfigManager.getBool(w, "bgToggle", true);
        int bgColor = WidgetConfigManager.getInt(w, "bgColor", 0x80000000);
        boolean brdToggle = WidgetConfigManager.getBool(w, "brdToggle", true);
        int brdColor = WidgetConfigManager.getInt(w, "brdColor", 0xFFFFFFFF);
        int textColor = WidgetConfigManager.getInt(w, "textColor", 0xFFFC5454);
        barToggle = WidgetConfigManager.getBool(w, "barToggle", false);

        String text = getHealth();
        int width = client.textRenderer.getWidth(text);
        int height = client.textRenderer.fontHeight;
        cachedTextWidth = width;
        cachedTextHeight = height;

        if (barToggle) {
            hpBar.render(context);
        }

        if (bgToggle) {
            context.fill((int) (x - 3), (int) (y - 3), (int) (x + width + 2), (int) (y + height + 2), bgColor);
        }

        if (brdToggle) {
            drawBorder(context, (int) (x - 3), (int) (y - 3), width + 5, height + 5, brdColor);
        }

        context.drawText(client.textRenderer, text, (int) x, (int) y, textColor, false);
        syncBarPosition();
    }

    private String getHealth() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return health;
        }

        InGameHudAccessor hud = (InGameHudAccessor) client.inGameHud;
        Text overlay = hud.legends$getOverlayMessage();
        if (overlay == null) {
            return health;
        }

        Matcher matcher = HEALTH_PATTERN.matcher(overlay.getString());
        if (matcher.find()) {
            String current = firstNonNull(matcher.group(1), matcher.group(3));
            String max = firstNonNull(matcher.group(2), matcher.group(4));
            if (current == null || max == null) {
                return health;
            }

            currentHealth = Double.parseDouble(current.replace(',', '.'));
            maxHealth = Double.parseDouble(max.replace(',', '.'));
            health = currentHealth + "/" + maxHealth;
            hpBar.setMin(currentHealth);
            hpBar.setMax(maxHealth);
        }

        return health;
    }

    public boolean isMouseOverBar(double mouseX, double mouseY) {
        if (!WidgetConfigManager.getBool(name, "barToggle", false)) {
            return false;
        }

        syncBarPosition();
        return hpBar.isMouseOver(mouseX, mouseY);
    }

    public void moveBar(double dx, double dy) {
        syncBarPosition();
        hpBar.move(dx, dy);
        saveBarCoordinates(false);
    }

    public void clampBar(int screenWidth, int screenHeight) {
        syncBarPosition();
        hpBar.x = Math.max(0, Math.min(hpBar.x, screenWidth - hpBar.getWidth()));
        hpBar.y = Math.max(0, Math.min(hpBar.y, screenHeight - hpBar.getHeight()));
        saveBarCoordinates(false);
    }

    public void saveBarPosition() {
        saveBarCoordinates(false);
        WidgetConfigManager.updateWidget(this);
    }

    @Override
    public void onScreenSizeChanged(int oldWidth, int oldHeight, int newWidth, int newHeight) {
        syncBarPosition();
    }

    private void syncBarPosition() {
        hpBar.x = WidgetConfigManager.getAnchoredXSetting(name, "barX", (float) x);
        hpBar.y = WidgetConfigManager.getAnchoredYSetting(name, "barY", (float) (y + DEFAULT_BAR_Y_OFFSET));
    }

    private void saveBarCoordinates(boolean autosave) {
        WidgetConfigManager.setAnchoredXSetting(name, "barX", (float) hpBar.x, autosave);
        WidgetConfigManager.setAnchoredYSetting(name, "barY", (float) hpBar.y, autosave);
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    @Override
    public double getWidth() {
        if (cachedTextWidth > 0) {
            return cachedTextWidth;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return 0;
        }

        return client.textRenderer.getWidth(health);
    }

    @Override
    public double getHeight() {
        if (cachedTextHeight > 0) {
            return cachedTextHeight;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? 0 : client.textRenderer.fontHeight;
    }

    @Override
    public List<HudSetting> getSettings() {
        final String w = this.getName();

        return List.of(
                HudSetting.toggle("bgToggle", "Background",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, "bgToggle", false),
                        b -> WidgetConfigManager.setBool(w, "bgToggle", b, true),
                        false
                ),
                HudSetting.color("bgColor", "BG Color",
                        () -> WidgetConfigManager.getBool(w, "bgToggle", true),
                        () -> WidgetConfigManager.getInt(w, "bgColor", 0x80000000),
                        c -> WidgetConfigManager.setInt(w, "bgColor", c, true),
                        0x00000000
                ),
                HudSetting.toggle("brdToggle", "Border",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, "brdToggle", false),
                        b -> WidgetConfigManager.setBool(w, "brdToggle", b, true),
                        false
                ),
                HudSetting.color("brdColor", "Border Color",
                        () -> WidgetConfigManager.getBool(w, "brdToggle", true),
                        () -> WidgetConfigManager.getInt(w, "brdColor", 0xFFFFFFFF),
                        c -> WidgetConfigManager.setInt(w, "brdColor", c, true),
                        0x00000000
                ),
                HudSetting.color("textColor", "Text Color",
                        () -> true,
                        () -> WidgetConfigManager.getInt(w, "textColor", 0xFFFC5454),
                        c -> WidgetConfigManager.setInt(w, "textColor", c, true),
                        0xFFFC5454
                ),
                HudSetting.toggle("barToggle", "Health Bar",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, "barToggle", false),
                        b -> WidgetConfigManager.setBool(w, "barToggle", b, true),
                        false
                ),
                HudSetting.slider(
                        "range", "Bar width",
                        1f, 200f, 1f,
                        () -> true,
                        () -> WidgetConfigManager.getFloat(w, "barWidth", 80f),
                        v -> WidgetConfigManager.setFloat(w, "barWidth", (float) v, true),
                        800f
                )
        );
    }
}
