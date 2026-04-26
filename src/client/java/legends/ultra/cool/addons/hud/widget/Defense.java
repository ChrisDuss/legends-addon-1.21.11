package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
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

public class Defense extends HudWidget {
    private static final Pattern DEF_PATTERN = Pattern.compile("❈\\s*(\\d+(?:[.,]\\d+)?)\\s*");

    private static Defense INSTANCE;

    private String defense = "0";
    private double cachedTextWidth = 0;
    private int cachedTextHeight = 0;

    public Defense(String name, double x, double y) {
        super(name, x, y);
        INSTANCE = this;
    }

    public static boolean isEnabledGlobal() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    public static boolean shouldHideOverlay(Text overlay) {
        return isEnabledGlobal() && overlay != null && findDefenseMatch(overlay.getString()) != null;
    }

    public static Text stripDefenseOverlay(Text overlay) {
        if (!isEnabledGlobal() || overlay == null) {
            return overlay;
        }

        DefenseMatch match = findDefenseMatch(overlay.getString());
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
        int textColor = WidgetConfigManager.getInt(w, "textColor", 0xFF54FC54);

        String text = getDefense();
        int width = client.textRenderer.getWidth(text);
        int height = client.textRenderer.fontHeight;
        cachedTextWidth = width;
        cachedTextHeight = height;

        if (bgToggle) {
            context.fill((int) (x - 3), (int) (y - 3), (int) (x + width + 2), (int) (y + height + 2), bgColor);
        }

        if (brdToggle) {
            drawBorder(context, (int) (x - 3), (int) (y - 3), width + 5, height + 5, brdColor);
        }

        context.drawText(client.textRenderer, text, (int) x, (int) y, textColor, !bgToggle);
    }

    private String getDefense() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return defense;
        }

        InGameHudAccessor hud = (InGameHudAccessor) client.inGameHud;
        Text overlay = hud.legends$getOverlayMessage();
        if (overlay == null) {
            return defense;
        }

        DefenseMatch match = findDefenseMatch(overlay.getString());
        if (match != null) {
            defense = match.value();
        }

        return defense;
    }

    private static DefenseMatch findDefenseMatch(String text) {
        Matcher matcher = DEF_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String value = matcher.group(1);
        if (value == null) {
            return null;
        }

        return new DefenseMatch(matcher.start(), matcher.end(), value);
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

    private record DefenseMatch(int start, int end, String value) {
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

        return client.textRenderer.getWidth(defense);
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
                        () -> WidgetConfigManager.getBool(w, "bgToggle", true),
                        b -> WidgetConfigManager.setBool(w, "bgToggle", b, true),
                        true
                ),
                HudSetting.color("bgColor", "BG Color",
                        () -> WidgetConfigManager.getBool(w, "bgToggle", true),
                        () -> WidgetConfigManager.getInt(w, "bgColor", 0x80000000),
                        c -> WidgetConfigManager.setInt(w, "bgColor", c, true),
                        0x80000000
                ),
                HudSetting.toggle("brdToggle", "Border",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, "brdToggle", true),
                        b -> WidgetConfigManager.setBool(w, "brdToggle", b, true),
                        true
                ),
                HudSetting.color("brdColor", "Border Color",
                        () -> WidgetConfigManager.getBool(w, "brdToggle", true),
                        () -> WidgetConfigManager.getInt(w, "brdColor", 0xFFFFFFFF),
                        c -> WidgetConfigManager.setInt(w, "brdColor", c, true),
                        0xFFFFFFFF
                ),
                HudSetting.color("textColor", "Text Color",
                        () -> true,
                        () -> WidgetConfigManager.getInt(w, "textColor", 0xFF54FC54),
                        c -> WidgetConfigManager.setInt(w, "textColor", c, true),
                        0xFF54FC54
                )
        );
    }
}
