package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.BarDraggable;
import legends.ultra.cool.addons.hud.HudWidget;
import legends.ultra.cool.addons.mixin.client.InGameHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Mana extends HudWidget implements BarDraggable {
    private static final Pattern MANA_PATTERN = Pattern.compile("✎(\\s*(\\d+(?:[.,]\\d+)?)\\s*/\\s*(\\d+(?:[.,]\\d+)?)\\s*)✎");
    private static final float DEFAULT_BAR_Y_OFFSET = 13f;

    private static Mana INSTANCE;

    private String mana = "0";
    private double maxMana = 0;
    private double currentMana = 0;
    private double cachedTextWidth = 0;
    private int cachedTextHeight = 0;

    private final Bar manaBar;

    public static boolean barToggle = false;

    public Mana(String name, int x, int y) {
        super(name, x, y);
        this.manaBar = new Bar(name, "ManaBar", x, y + DEFAULT_BAR_Y_OFFSET, currentMana, maxMana);
        INSTANCE = this;
    }

    public static boolean isEnabledGlobal() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    public static boolean shouldHideOverlay(Text overlay) {
        return isEnabledGlobal() && overlay != null && findManaMatch(overlay.getString()) != null;
    }

    public static Text stripManaOverlay(Text overlay) {
        if (!isEnabledGlobal() || overlay == null) {
            return overlay;
        }

        ManaMatch match = findManaMatch(overlay.getString());
        if (match == null) {
            return overlay;
        }

        Text stripped = removeMatchedRange(overlay, match.start(), match.end());
        return stripped.getString().isBlank() ? Text.empty() : stripped;
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
        int textColor = WidgetConfigManager.getInt(w, "textColor", 0xFF0b50ad);
        barToggle = WidgetConfigManager.getBool(w, "barToggle", false);

        String text = getMana();
        int width = client.textRenderer.getWidth(text);
        int height = client.textRenderer.fontHeight;
        cachedTextWidth = width;
        cachedTextHeight = height;

        manaBar.setBarColor(0xFF0b50ad);

        if (barToggle) {
            manaBar.render(context);
        }

        if (bgToggle) {
            context.fill((int) (x - 3), (int) (y - 3), (int) (x + width + 2), (int) (y + height + 2), bgColor);
        }

        if (brdToggle) {
            drawBorder(context, (int) (x - 3), (int) (y - 3), width + 5, height + 5, brdColor);
        }

        context.drawText(client.textRenderer, text, (int) x, (int) y, textColor, !bgToggle);
        syncBarPosition();
    }

    private String getMana() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return mana = "0";
        }

        InGameHudAccessor hud = (InGameHudAccessor) client.inGameHud;
        Text overlay = hud.legends$getOverlayMessage();
        if (overlay == null) {
            return mana = "0";
        }

        ManaMatch match = findManaMatch(overlay.getString());
        if (match != null) {
            currentMana = Double.parseDouble(match.current().replace(',', '.'));
            maxMana = Double.parseDouble(match.max().replace(',', '.'));
            mana = currentMana + "/" + maxMana;
            manaBar.setMin(currentMana);
            manaBar.setMax(maxMana);
        } else {
            currentMana = 0;
            maxMana = 0;
            mana = currentMana + "/" + maxMana;
            manaBar.setMin(currentMana);
            manaBar.setMax(maxMana);
        }

        return mana;
    }

    public boolean isMouseOverBar(double mouseX, double mouseY) {
        if (!WidgetConfigManager.getBool(name, "barToggle", false)) {
            return false;
        }

        syncBarPosition();
        return manaBar.isMouseOver(mouseX, mouseY);
    }

    public void moveBar(double dx, double dy) {
        syncBarPosition();
        manaBar.move(dx, dy);
        saveBarCoordinates(false);
    }

    public void clampBar(int screenWidth, int screenHeight) {
        syncBarPosition();
        manaBar.clampToScreen(screenWidth, screenHeight);
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
        manaBar.x = WidgetConfigManager.getAnchoredXSetting(name, "barX", (float) x);
        manaBar.y = WidgetConfigManager.getAnchoredYSetting(name, "barY", (float) (y + DEFAULT_BAR_Y_OFFSET));
    }

    private void saveBarCoordinates(boolean autosave) {
        WidgetConfigManager.setAnchoredXSetting(name, "barX", (float) manaBar.x, autosave);
        WidgetConfigManager.setAnchoredYSetting(name, "barY", (float) manaBar.y, autosave);
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    private static ManaMatch findManaMatch(String text) {
        Matcher matcher = MANA_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String current = matcher.group(2);
        String max = matcher.group(3);
        if (current == null || max == null) {
            return null;
        }

        return new ManaMatch(matcher.start(), matcher.end(), current, max);
    }

    private static Text removeMatchedRange(Text overlay, int start, int end) {
        MutableText stripped = Text.empty();
        int[] cursor = {0};

        overlay.visit((style, text) -> {
            int segmentStart = cursor[0];
            int segmentEnd = segmentStart + text.length();
            cursor[0] = segmentEnd;

            if (end <= segmentStart || start >= segmentEnd) {
                appendSegment(stripped, text, style);
                return Optional.empty();
            }

            if (start > segmentStart) {
                appendSegment(stripped, text.substring(0, start - segmentStart), style);
            }

            if (end < segmentEnd) {
                appendSegment(stripped, text.substring(end - segmentStart), style);
            }

            return Optional.empty();
        }, Style.EMPTY);

        return stripped;
    }

    private static void appendSegment(MutableText target, String text, Style style) {
        if (!text.isEmpty()) {
            target.append(Text.literal(text).setStyle(style));
        }
    }

    private record ManaMatch(int start, int end, String current, String max) {
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

        return client.textRenderer.getWidth(mana);
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
                HudSetting.section("Display"),
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
                        () -> WidgetConfigManager.getInt(w, "textColor", 0xFF0b50ad),
                        c -> WidgetConfigManager.setInt(w, "textColor", c, true),
                        0xFF0b50ad
                ),
                HudSetting.section("Bar"),
                HudSetting.toggle("barToggle", "Mana Bar",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, "barToggle", false),
                        b -> WidgetConfigManager.setBool(w, "barToggle", b, true),
                        false
                ),
                HudSetting.toggle("invertToggle", "Invert Bar",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, "invertToggle", true),
                        b -> WidgetConfigManager.setBool(w, "invertToggle", b, true),
                        true
                ),
                HudSetting.slider(
                        "barWidth", "Bar width",
                        1f, 200f, 1f,
                        () -> true,
                        () -> WidgetConfigManager.getFloat(w, "barWidth", 80f),
                        v -> WidgetConfigManager.setFloat(w, "barWidth", (float) v, true),
                        80f
                ),
                HudSetting.toggle("foodToggle", "Hide Food bar",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, "foodToggle", false),
                        b -> WidgetConfigManager.setBool(w, "foodToggle", b, true),
                        false
                )
        );
    }
}
