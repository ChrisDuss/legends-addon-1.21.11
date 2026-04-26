package legends.ultra.cool.addons.hud.widget.otherTypes;

import legends.ultra.cool.addons.hud.HudWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerLikeEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NpcChatWidget extends HudWidget {
    public static final String WIDGET_NAME = "Better Dialogue";
    private static final String PREFIX = "[NPC]";
    private static final Pattern NPC_DIALOGUE_PATTERN = Pattern.compile("^\\[NPC\\]\\s+(.+?)\\s*>>\\s*(.+)$");
    private static final int DIALOGUE_DURATION_TICKS = 160;
    private static final int DIALOGUE_WRAP_WIDTH = 160;

    public static NpcChatWidget INSTANCE;

    private static String activeSpeakerName;
    private static Text activeMessage;
    private static int activeTargetEntityId = -1;
    private static int activeUntilTick = Integer.MIN_VALUE;
    private static int lastResolveTick = Integer.MIN_VALUE;

    public NpcChatWidget() {
        super(WIDGET_NAME, 0, 0);
        INSTANCE = this;
    }

    public static boolean isEnabledGlobal() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    public static boolean isNpcMessage(Text message) {
        if (message == null) return false;
        String raw = message.getString();
        return raw != null && raw.startsWith(PREFIX);
    }

    public static boolean captureNpcMessage(Text message) {
        if (message == null) {
            clearActiveDialogue();
            return false;
        }

        DialogueMatch match = parseDialogue(message.getString());
        if (match == null) {
            clearActiveDialogue();
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        String speakerName = normalizeName(match.speakerName());
        Text messageText = extractStyledSubstring(message, match.messageStart(), match.messageEnd());
        int targetEntityId = resolveClosestNpcEntityId(client, speakerName);
        if (targetEntityId < 0 || messageText == null || messageText.getString().isBlank()) {
            clearActiveDialogue();
            return false;
        }

        activeSpeakerName = speakerName;
        activeMessage = messageText;
        activeUntilTick = getCurrentTick(client) + DIALOGUE_DURATION_TICKS;
        activeTargetEntityId = targetEntityId;
        lastResolveTick = Integer.MIN_VALUE;
        return true;
    }

    public static Text getDialogueLabel(Entity entity) {
        if (!isEnabledGlobal() || entity == null) {
            return null;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (!hasActiveDialogue(client)) {
            return null;
        }

        ensureResolvedTarget(client);
        if (entity.getId() != activeTargetEntityId) {
            return null;
        }

        return activeMessage.copy();
    }

    @Override
    public void render(DrawContext context) {}

    @Override
    public double getWidth() {
        return 1;
    }

    @Override
    public double getHeight() {
        return 1;
    }

    @Override
    public boolean hasSettings() {
        return false;
    }

    private static boolean hasActiveDialogue(MinecraftClient client) {
        if (activeSpeakerName == null || activeMessage == null) {
            return false;
        }

        int now = getCurrentTick(client);
        if (now > activeUntilTick) {
            clearActiveDialogue();
            return false;
        }

        return true;
    }

    private static void ensureResolvedTarget(MinecraftClient client) {
        int now = getCurrentTick(client);
        if (now == lastResolveTick) {
            return;
        }

        lastResolveTick = now;

        if (client == null || client.world == null) {
            activeTargetEntityId = -1;
            return;
        }

        Entity current = activeTargetEntityId >= 0 ? client.world.getEntityById(activeTargetEntityId) : null;
        if (current != null && matchesSpeaker(current, activeSpeakerName)) {
            return;
        }

        activeTargetEntityId = resolveClosestNpcEntityId(client, activeSpeakerName);
    }

    private static int resolveClosestNpcEntityId(MinecraftClient client, String speakerName) {
        if (client == null || client.world == null || client.player == null || speakerName == null || speakerName.isBlank()) {
            return -1;
        }

        int bestScore = Integer.MIN_VALUE;
        double bestDistance = Double.MAX_VALUE;
        int bestEntityId = -1;

        for (Entity entity : client.world.getEntities()) {
            if (!isEligibleNpcEntity(client, entity)) continue;
            int score = getSpeakerMatchScore(entity, speakerName);
            if (score < 0) continue;

            double distance = entity.squaredDistanceTo(client.player);
            if (score > bestScore || (score == bestScore && distance < bestDistance)) {
                bestScore = score;
                bestDistance = distance;
                bestEntityId = entity.getId();
            }
        }

        return bestEntityId;
    }

    private static boolean isEligibleNpcEntity(MinecraftClient client, Entity entity) {
        if (entity == null || entity == client.player) {
            return false;
        }

        return entity instanceof ClientPlayerLikeEntity || entity instanceof ArmorStandEntity;
    }

    private static boolean matchesSpeaker(Entity entity, String speakerName) {
        return getSpeakerMatchScore(entity, speakerName) >= 0;
    }

    private static int getSpeakerMatchScore(Entity entity, String speakerName) {
        if (entity == null) {
            return -1;
        }

        String normalizedTarget = normalizeName(speakerName);
        if (normalizedTarget.isBlank()) {
            return -1;
        }

        int bestScore = -1;
        for (String candidate : getCandidateNames(entity)) {
            int score = scoreNameMatch(normalizedTarget, candidate);
            if (score > bestScore) {
                bestScore = score;
            }
        }

        return bestScore;
    }

    private static Set<String> getCandidateNames(Entity entity) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addCandidateName(candidates, getMannequinName(entity));
        addCandidateName(candidates, entity.getDisplayName().getString());
        addCandidateName(candidates, entity.getName().getString());

        Text customName = entity.getCustomName();
        if (customName != null) {
            addCandidateName(candidates, customName.getString());
        }

        if (entity instanceof PlayerEntity player) {
            addCandidateName(candidates, player.getGameProfile().name());
        }

        return candidates;
    }

    private static void addCandidateName(Set<String> candidates, String name) {
        String normalized = normalizeName(name);
        if (!normalized.isBlank()) {
            candidates.add(normalized);
        }
    }

    private static int scoreNameMatch(String target, String candidate) {
        if (target.isBlank() || candidate.isBlank()) {
            return -1;
        }

        if (candidate.equals(target)) {
            return 1000;
        }

        if (candidate.endsWith(" " + target)) {
            return 900;
        }

        if (containsWholePhrase(candidate, target)) {
            return 800;
        }

        if (target.endsWith(" " + candidate)) {
            return 700;
        }

        if (candidate.contains(target)) {
            return 600;
        }

        int targetWordMatches = countTargetWordMatches(target, candidate);
        if (targetWordMatches > 0) {
            return 500 + targetWordMatches;
        }

        int editDistance = levenshteinDistance(target, candidate);
        int maxLength = Math.max(target.length(), candidate.length());
        int allowedDistance = Math.max(1, maxLength / 3);
        if (editDistance <= allowedDistance) {
            return 300 - editDistance;
        }

        return -1;
    }

    private static boolean containsWholePhrase(String candidate, String target) {
        if (candidate.equals(target)) {
            return true;
        }

        return candidate.startsWith(target + " ")
                || candidate.endsWith(" " + target)
                || candidate.contains(" " + target + " ");
    }

    private static int countTargetWordMatches(String target, String candidate) {
        String[] targetWords = target.split(" ");
        String[] candidateWords = candidate.split(" ");
        int matches = 0;

        for (String targetWord : targetWords) {
            if (targetWord.isBlank()) {
                continue;
            }

            for (String candidateWord : candidateWords) {
                if (candidateWord.equals(targetWord)) {
                    matches++;
                    break;
                }
            }
        }

        return matches;
    }

    private static int levenshteinDistance(String left, String right) {
        int[] prev = new int[right.length() + 1];
        int[] curr = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            curr[0] = i;
            char leftChar = left.charAt(i - 1);

            for (int j = 1; j <= right.length(); j++) {
                int cost = leftChar == right.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }

            int[] swap = prev;
            prev = curr;
            curr = swap;
        }

        return prev[right.length()];
    }

    private static String getMannequinName(Entity entity) {
        if (entity instanceof ClientPlayerLikeEntity playerLike) {
            Text mannequinName = playerLike.getMannequinName();
            if (mannequinName != null) {
                return mannequinName.getString();
            }
        }

        return "";
    }

    private static DialogueMatch parseDialogue(String raw) {
        if (raw == null) {
            return null;
        }

        Matcher matcher = NPC_DIALOGUE_PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            return null;
        }

        String speakerName = matcher.group(1);
        String message = matcher.group(2);
        if (speakerName == null || message == null) {
            return null;
        }

        int messageStart = matcher.start(2);
        int messageEnd = matcher.end(2);

        while (messageStart < messageEnd && Character.isWhitespace(raw.charAt(messageStart))) {
            messageStart++;
        }

        while (messageEnd > messageStart && Character.isWhitespace(raw.charAt(messageEnd - 1))) {
            messageEnd--;
        }

        return new DialogueMatch(speakerName, message.trim(), messageStart, messageEnd);
    }

    private static int getCurrentTick(MinecraftClient client) {
        if (client == null || client.inGameHud == null) {
            return 0;
        }

        return client.inGameHud.getTicks();
    }

    public static int getDialogueWrapWidth() {
        return DIALOGUE_WRAP_WIDTH;
    }

    private static Text extractStyledSubstring(Text source, int start, int end) {
        if (source == null || start >= end) {
            return null;
        }

        MutableText result = Text.empty();
        int[] index = {0};

        source.visit((style, segment) -> {
            int segmentStart = index[0];
            int segmentEnd = segmentStart + segment.length();
            int copyStart = Math.max(start, segmentStart);
            int copyEnd = Math.min(end, segmentEnd);

            if (copyStart < copyEnd) {
                String piece = segment.substring(copyStart - segmentStart, copyEnd - segmentStart);
                result.append(Text.literal(piece).setStyle(style));
            }

            index[0] = segmentEnd;
            return Optional.empty();
        }, Style.EMPTY);

        return result;
    }

    private static void clearActiveDialogue() {
        activeSpeakerName = null;
        activeMessage = null;
        activeTargetEntityId = -1;
        activeUntilTick = Integer.MIN_VALUE;
        lastResolveTick = Integer.MIN_VALUE;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }

        int bracket = name.indexOf('[');
        if (bracket > 0) {
            name = name.substring(0, bracket);
        }

        name = name
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                .toLowerCase(Locale.ROOT)
                .trim();

        return name.replaceAll("\\s+", " ");
    }

    private record DialogueMatch(String speakerName, String message, int messageStart, int messageEnd) {
    }
}
