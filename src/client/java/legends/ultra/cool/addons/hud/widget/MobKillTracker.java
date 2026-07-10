package legends.ultra.cool.addons.hud.widget;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.HudEditorScreen;
import legends.ultra.cool.addons.hud.HudWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class MobKillTracker extends HudWidget {
    private static final String ENTRIES_KEY = "trackedMobs";
    private static final String MAX_MOBS_KEY = "maxMobs";
    private static final String EXPIRY_SECONDS_KEY = "expirySeconds";
    private static final String RESET_ON_RESTART_KEY = "resetOnRestart";
    private static final String COUNT_COLOR_KEY = "countColor";
    private static final String RANGED_MAGIC_KEY = "rangedMagicAttribution";
    private static final String BLACKLIST_KEY = "blacklist";
    private static final String WHITELIST_KEY = "whitelist";
    private static final String BLACKLIST_TOGGLE_KEY = "blacklistToggle";
    private static final String WHITELIST_TOGGLE_KEY = "whitelistToggle";
    private static final Pattern HEART_HEALTH_PATTERN = Pattern.compile("(?i)[♥❤]\\s*\\d+(?:[.,]\\d+)?\\s*/\\s*\\d+(?:[.,]\\d+)?\\s*[♥❤]?");
    private static final Pattern HEALTH_WORD_PATTERN = Pattern.compile("(?i)\\b(?:hp|health)\\s*[:\\-]?\\s*\\d+(?:[.,]\\d+)?\\s*/\\s*\\d+(?:[.,]\\d+)?\\b");
    private static final Pattern HEALTH_SUFFIX_PATTERN = Pattern.compile("(?i)(?:\\s*[-|•]\\s*)?\\d+(?:[.,]\\d+)?\\s*/\\s*\\d+(?:[.,]\\d+)?\\s*(?:hp|health)?\\b");

    private static final int DEFAULT_MAX_MOBS = 4;
    private static final int DEFAULT_EXPIRY_SECONDS = 0;
    private static final int DEFAULT_COUNT_COLOR = 0xFFFF5555;
    private static final int ATTACK_MEMORY_TICKS = 200;
    private static final int RANGED_CANDIDATE_TICKS = 80;
    private static final int MAGIC_ACTION_WINDOW_TICKS = 45;
    private static final int PROJECTILE_MEMORY_TICKS = 120;
    private static final int OTHER_PROJECTILE_BLOCK_TICKS = 100;
    private static final double AIM_RANGE = 64d;
    private static final double AOE_RADIUS_SQ = 18d * 18d;
    private static final double PROJECTILE_TARGET_DISTANCE_SQ = 16d;
    private static final double PROJECTILE_SPAWN_DISTANCE_SQ = 14d * 14d;
    private static final double OTHER_PLAYER_CONTEST_RADIUS_SQ = 14d * 14d;

    private static final int ENTITY_BOX = 30;
    private static final int ROW_GAP = 2;
    private static final int TEXT_GAP = 5;
    private static final int PAD = 3;
    private static final int FALLBACK_BOX = 18;
    private static final float PREVIEW_BODY_YAW = 225f;
    private static final float PREVIEW_RENDER_X_SLANT = -22f;
    private static final float PREVIEW_TARGET_PIXELS = 25f;
    private static final List<EquipmentSlot> FINGERPRINT_EQUIPMENT_SLOTS = List.of(
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.BODY,
            EquipmentSlot.SADDLE
    );

    private static MobKillTracker instance;

    private final List<MobEntry> entries = new ArrayList<>();
    private final Map<Integer, RecentAttack> recentAttacks = new HashMap<>();
    private final Map<Integer, RangedCandidate> recentRangedCandidates = new HashMap<>();
    private final Map<Integer, Long> possiblePlayerProjectiles = new HashMap<>();
    private final Map<Integer, Long> possibleOtherPlayerProjectiles = new HashMap<>();
    private final Map<Integer, Long> blockedRangedTargets = new HashMap<>();
    private final Set<UUID> countedDeaths = new HashSet<>();
    private boolean needsInitialEntrySave = false;
    private long lastMagicActionTick = Long.MIN_VALUE;

    public MobKillTracker(int x, int y) {
        super("Mob Tracker", x, y);
        instance = this;
        loadInitialEntries();
    }

    public static void recordAttack(Entity entity) {
        if (instance != null) {
            instance.rememberAttack(entity);
        }
    }

    public static void recordDeathStatus(LivingEntity entity, byte status) {
        if (instance == null) {
            return;
        }
        if (status == EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES
                || status == EntityStatuses.ADD_DEATH_PARTICLES) {
            instance.handleDeathSignal(entity);
        }
    }

    public static void clearTransientTracking() {
        if (instance != null) {
            instance.recentAttacks.clear();
            instance.recentRangedCandidates.clear();
            instance.possiblePlayerProjectiles.clear();
            instance.possibleOtherPlayerProjectiles.clear();
            instance.blockedRangedTargets.clear();
            instance.countedDeaths.clear();
            instance.lastMagicActionTick = Long.MIN_VALUE;
        }
    }

    public static void recordMagicAction() {
        if (instance != null) {
            instance.rememberMagicAction();
        }
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            recentAttacks.clear();
            recentRangedCandidates.clear();
            possiblePlayerProjectiles.clear();
            possibleOtherPlayerProjectiles.clear();
            blockedRangedTargets.clear();
            countedDeaths.clear();
            lastMagicActionTick = Long.MIN_VALUE;
            return;
        }

        flushInitialEntrySaveIfNeeded();
        long nowTick = client.inGameHud.getTicks();
        boolean changed = purgeExpiredEntries(System.currentTimeMillis());
        if (isRangedMagicAttributionEnabled()) {
            updateRangedCandidates(client, nowTick);
        } else {
            recentRangedCandidates.clear();
        }

        Iterator<Map.Entry<Integer, RecentAttack>> iterator = recentAttacks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, RecentAttack> attackEntry = iterator.next();
            RecentAttack attack = attackEntry.getValue();
            if (nowTick - attack.attackTick > ATTACK_MEMORY_TICKS) {
                iterator.remove();
                continue;
            }

            Entity entity = client.world.getEntityById(attackEntry.getKey());
            if (entity instanceof LivingEntity living && isDeathState(living)) {
                countKill(living, attack.snapshot);
                iterator.remove();
            }
        }

        if (changed) {
            saveEntries();
        }
    }

    private void updateRangedCandidates(MinecraftClient client, long nowTick) {
        purgeRangedCandidates(nowTick);
        purgeProjectileCandidates(client, nowTick);
        purgeBlockedRangedTargets(nowTick);

        MobEntity aimedMob = findAimedMob(client);
        if (aimedMob != null) {
            recentRangedCandidates.put(aimedMob.getId(), new RangedCandidate(snapshotMob(aimedMob), nowTick));
        }

        if (isInRecentMagicActionWindow(nowTick)) {
            markNearbyAoeCandidates(client, nowTick);
        }

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof ProjectileEntity projectile)) {
                continue;
            }
            if (isOtherPlayerProjectileCandidate(projectile, client, nowTick)) {
                MobEntity blockedMob = findNearestMobToProjectile(client, projectile);
                if (blockedMob != null) {
                    blockedRangedTargets.put(blockedMob.getId(), nowTick);
                    recentRangedCandidates.remove(blockedMob.getId());
                }
                continue;
            }

            if (!isPlayerProjectileCandidate(projectile, client.player, nowTick)) {
                continue;
            }

            MobEntity nearbyMob = findNearestMobToProjectile(client, projectile);
            if (nearbyMob != null) {
                recentRangedCandidates.put(nearbyMob.getId(), new RangedCandidate(snapshotMob(nearbyMob), nowTick));
            }
        }
    }

    private void purgeRangedCandidates(long nowTick) {
        recentRangedCandidates.entrySet().removeIf(entry -> nowTick - entry.getValue().tick > RANGED_CANDIDATE_TICKS);
    }

    private void purgeProjectileCandidates(MinecraftClient client, long nowTick) {
        possiblePlayerProjectiles.entrySet().removeIf(entry -> {
            Entity entity = client.world.getEntityById(entry.getKey());
            return entity == null
                    || entity.isRemoved()
                    || nowTick - entry.getValue() > PROJECTILE_MEMORY_TICKS;
        });
        possibleOtherPlayerProjectiles.entrySet().removeIf(entry -> {
            Entity entity = client.world.getEntityById(entry.getKey());
            return entity == null
                    || entity.isRemoved()
                    || nowTick - entry.getValue() > PROJECTILE_MEMORY_TICKS;
        });
    }

    private void purgeBlockedRangedTargets(long nowTick) {
        blockedRangedTargets.entrySet().removeIf(entry -> nowTick - entry.getValue() > OTHER_PROJECTILE_BLOCK_TICKS);
    }

    private void markNearbyAoeCandidates(MinecraftClient client, long nowTick) {
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof MobEntity mob) || !mob.isAlive()) {
                continue;
            }
            if (mob.squaredDistanceTo(client.player) <= AOE_RADIUS_SQ) {
                if (!hasCompetingOtherPlayer(client, mob)) {
                    recentRangedCandidates.put(mob.getId(), new RangedCandidate(snapshotMob(mob), nowTick));
                }
            }
        }
    }

    private void rememberMagicAction() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client.player == null || !isRangedMagicAttributionEnabled() || !isHoldingItem(client.player)) {
            return;
        }

        lastMagicActionTick = client.inGameHud.getTicks();
    }

    private void rememberAttack(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client.player == null || !(entity instanceof MobEntity mob)) {
            return;
        }

        recentAttacks.put(entity.getId(), new RecentAttack(
                entity.getId(),
                entity.getUuid(),
                snapshotMob(mob),
                client.inGameHud.getTicks()
        ));
    }

    private void handleDeathSignal(LivingEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client.player == null || !(entity instanceof MobEntity)) {
            return;
        }

        RecentAttack attack = recentAttacks.remove(entity.getId());
        if (attack != null) {
            countKill(entity, attack.snapshot);
            return;
        }

        PlayerEntity attackingPlayer = entity.getAttackingPlayer();
        if (attackingPlayer != null && attackingPlayer.getUuid().equals(client.player.getUuid())) {
            countKill(entity, snapshotMob(entity));
            return;
        }

        if (isRangedMagicAttributionEnabled()) {
            RangedCandidate candidate = recentRangedCandidates.remove(entity.getId());
            long nowTick = client.inGameHud.getTicks();
            if (candidate != null
                    && nowTick - candidate.tick <= RANGED_CANDIDATE_TICKS
                    && !isBlockedRangedTarget(entity.getId(), nowTick)
                    && !hasCompetingOtherPlayer(client, entity)) {
                countKill(entity, candidate.snapshot);
            }
        }
    }

    private void countKill(LivingEntity entity, MobSnapshot snapshot) {
        if (entity == null || snapshot == null || snapshot.typeId == null || snapshot.typeId.isBlank()) {
            return;
        }

        UUID uuid = entity.getUuid();
        if (!countedDeaths.add(uuid)) {
            return;
        }

        String safeName = snapshot.displayName == null || snapshot.displayName.isBlank()
                ? entity.getType().getName().getString()
                : snapshot.displayName;
        if (!shouldTrackMob(snapshot, safeName)) {
            return;
        }

        long now = System.currentTimeMillis();
        purgeExpiredEntries(now);

        String key = entryKey(snapshot);
        for (int i = 0; i < entries.size(); i++) {
            MobEntry entry = entries.get(i);
            if (!key.equals(entry.key)) {
                continue;
            }

            entry.count++;
            entry.lastUpdatedEpochMs = now;
            entry.name = safeName;
            entry.equipment = snapshot.equipment;
            entries.remove(i);
            entries.add(0, entry);
            trimEntries();
            saveEntries();
            return;
        }

        entries.add(0, new MobEntry(key, snapshot.typeId, safeName, snapshot.equipment, 1, now));
        trimEntries();
        saveEntries();
    }

    private void loadInitialEntries() {
        entries.clear();
        if (WidgetConfigManager.getBool(getName(), RESET_ON_RESTART_KEY, false)) {
            needsInitialEntrySave = true;
            return;
        }

        int loadedCount = 0;
        for (MobEntry entry : WidgetConfigManager.getObjectList(getName(), ENTRIES_KEY, MobEntry.class)) {
            loadedCount++;
            if (isValidEntry(entry)) {
                entry.equipment = sanitizeEquipment(entry.equipment);
                entry.key = entryKey(new MobSnapshot(entry.typeId, entry.name, entry.equipment));
                entries.add(entry);
            }
        }

        trimEntries();
        if (loadedCount != entries.size() || purgeExpiredEntries(System.currentTimeMillis())) {
            needsInitialEntrySave = true;
        }
    }

    private boolean purgeExpiredEntries(long now) {
        int expirySeconds = getExpirySeconds();
        if (expirySeconds <= 0 || entries.isEmpty()) {
            return false;
        }

        long expiryMillis = expirySeconds * 1000L;
        return entries.removeIf(entry -> now - Math.max(0L, entry.lastUpdatedEpochMs) >= expiryMillis);
    }

    private void trimEntries() {
        int maxMobs = getMaxMobs();
        while (entries.size() > maxMobs) {
            entries.remove(entries.size() - 1);
        }
    }

    private void saveEntries() {
        WidgetConfigManager.setObjectList(getName(), ENTRIES_KEY, entries, true);
        needsInitialEntrySave = false;
    }

    private void flushInitialEntrySaveIfNeeded() {
        if (needsInitialEntrySave) {
            saveEntries();
        }
    }

    private static boolean isDeathState(LivingEntity entity) {
        return entity.deathTime > 0 || !entity.isAlive() || entity.isRemoved();
    }

    @Override
    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        flushInitialEntrySaveIfNeeded();
        boolean editorPreview = client.currentScreen instanceof HudEditorScreen;
        List<MobEntry> rows = entries.isEmpty() && editorPreview ? placeholderRows() : List.copyOf(entries);
        if (rows.isEmpty()) {
            return;
        }

        final String w = getName();
        boolean bgToggle = WidgetConfigManager.getBool(w, "bgToggle", true);
        int bgColor = WidgetConfigManager.getInt(w, "bgColor", 0x80000000);
        boolean brdToggle = WidgetConfigManager.getBool(w, "brdToggle", true);
        int brdColor = WidgetConfigManager.getInt(w, "brdColor", 0xFFFFFFFF);
        int countColor = WidgetConfigManager.getInt(w, COUNT_COLOR_KEY, DEFAULT_COUNT_COLOR);

        int width = getRowsWidth(textRenderer, rows);
        int height = getRowsHeight(rows);
        int left = (int) x;
        int top = getRenderTop(height);

        if (bgToggle) {
            context.fill(left - PAD, top - PAD, left + width + PAD, top + height + PAD, bgColor);
        }

        if (brdToggle) {
            drawBorder(context, left - PAD, top - PAD, width + PAD * 2, height + PAD * 2, brdColor);
        }

        for (int i = 0; i < rows.size(); i++) {
            MobEntry entry = rows.get(i);
            int rowY = top + i * (ENTITY_BOX + ROW_GAP);
            drawMobPreview(context, client, entry, left, rowY);

            String countText = "x" + Math.max(0, entry.count) + " kills";
            context.drawText(
                    textRenderer,
                    countText,
                    left + ENTITY_BOX + TEXT_GAP,
                    rowY + (ENTITY_BOX - textRenderer.fontHeight) / 2,
                    countColor,
                    !bgToggle
            );
        }
    }

    private void drawMobPreview(DrawContext context, MinecraftClient client, MobEntry entry, int left, int rowY) {
        LivingEntity preview = getPreviewEntity(entry, client.world);
        if (preview != null && !entry.renderFailed) {
            try {
                drawFixedHeadEntity(context, preview, left, rowY, left + ENTITY_BOX, rowY + ENTITY_BOX, previewScale(preview));
                return;
            } catch (RuntimeException ignored) {
                entry.renderFailed = true;
            }
        }

        int fallbackX = left + (ENTITY_BOX - FALLBACK_BOX) / 2;
        int fallbackY = rowY + (ENTITY_BOX - FALLBACK_BOX) / 2;
        context.fill(fallbackX, fallbackY, fallbackX + FALLBACK_BOX, fallbackY + FALLBACK_BOX, 0x66000000);
        drawBorder(context, fallbackX, fallbackY, FALLBACK_BOX, FALLBACK_BOX, 0x99FFFFFF);

        String initial = fallbackInitial(entry.name);
        int textX = fallbackX + (FALLBACK_BOX - client.textRenderer.getWidth(initial)) / 2;
        int textY = fallbackY + (FALLBACK_BOX - client.textRenderer.fontHeight) / 2 + 1;
        context.drawText(client.textRenderer, initial, textX, textY, 0xFFFFFFFF, false);
    }

    private LivingEntity getPreviewEntity(MobEntry entry, World world) {
        if (world == null || entry == null || entry.typeId == null || entry.typeId.isBlank()) {
            return null;
        }

        if (entry.previewEntity != null) {
            return entry.previewEntity;
        }

        EntityType<?> type = EntityType.get(entry.typeId).orElse(null);
        if (type == null) {
            Identifier id = Identifier.tryParse(entry.typeId);
            if (id != null) {
                type = EntityType.get(id.toString()).orElse(null);
            }
        }
        if (type == null) {
            return null;
        }

        Entity entity = type.create(world, SpawnReason.COMMAND);
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }

        living.setYaw(25f);
        living.setPitch(0f);
        living.bodyYaw = 25f;
        living.headYaw = 25f;
        living.setCustomName(Text.literal(entry.name == null ? "" : entry.name));
        applyEquipment(living, entry.equipment);
        entry.previewEntity = living;
        return living;
    }

    private static void applyEquipment(LivingEntity entity, List<EquipmentSnapshot> equipment) {
        for (EquipmentSnapshot snapshot : sanitizeEquipment(equipment)) {
            try {
                EquipmentSlot slot = EquipmentSlot.byName(snapshot.slot);
                Identifier itemId = Identifier.tryParse(snapshot.itemId);
                if (itemId == null) {
                    continue;
                }

                Item item = Registries.ITEM.get(itemId);
                ItemStack stack = snapshot.stack != null && !snapshot.stack.isEmpty()
                        ? snapshot.stack.copyWithCount(1)
                        : new ItemStack(item);
                if (!stack.isEmpty()) {
                    entity.equipStack(slot, stack);
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void drawFixedHeadEntity(DrawContext context, LivingEntity entity, int x1, int y1, int x2, int y2, int size) {
        EntityRenderer renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        EntityRenderState state = renderer.getAndUpdateRenderState(entity, 1f);
        state.light = 15728880;
        state.shadowPieces.clear();
        state.outlineColor = 0;

        if (state instanceof LivingEntityRenderState livingState) {
            livingState.bodyYaw = PREVIEW_BODY_YAW;
            livingState.relativeHeadYaw = 0f;
            livingState.pitch = 0f;
            livingState.baseScale = 1f;
        }

        float slantRadians = PREVIEW_RENDER_X_SLANT * ((float) Math.PI / 180f);
        Quaternionf rotation = new Quaternionf()
                .rotateZ((float) Math.PI)
                .rotateX(slantRadians);
        Quaternionf overrideRotation = new Quaternionf()
                .rotateX(slantRadians);
        Vector3f translation = new Vector3f(0f, state.height / 2f, 0f);

        context.addEntity(state, size, translation, rotation, overrideRotation, x1, y1, x2, y2);
    }

    private int previewScale(LivingEntity entity) {
        float largest = Math.max(entity.getWidth(), entity.getHeight());
        return Math.max(7, Math.min(19, Math.round(PREVIEW_TARGET_PIXELS / Math.max(1f, largest))));
    }

    @Override
    public double getWidth() {
        MinecraftClient client = MinecraftClient.getInstance();
        return getRowsWidth(client.textRenderer, entries.isEmpty() ? placeholderRows() : entries);
    }

    @Override
    public double getHeight() {
        return getRowsHeight(entries.isEmpty() ? placeholderRows() : entries);
    }

    @Override
    public double getVisualX() {
        return usesDecoratedBounds() ? x - PAD : x;
    }

    @Override
    public double getVisualY() {
        return usesDecoratedBounds()
                ? getRenderTop((int) Math.ceil(getHeight())) - PAD
                : getRenderTop((int) Math.ceil(getHeight()));
    }

    @Override
    public double getVisualWidth() {
        return getWidth() + (usesDecoratedBounds() ? PAD * 2d : 0d);
    }

    @Override
    public double getVisualHeight() {
        return getHeight() + (usesDecoratedBounds() ? PAD * 2d : 0d);
    }

    @Override
    public List<HudSetting> getSettings() {
        final String w = getName();
        normalizeFilterMode();

        return List.of(
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
                HudSetting.color(COUNT_COLOR_KEY, "Count Color",
                        () -> true,
                        () -> WidgetConfigManager.getInt(w, COUNT_COLOR_KEY, DEFAULT_COUNT_COLOR),
                        c -> WidgetConfigManager.setInt(w, COUNT_COLOR_KEY, c, true),
                        DEFAULT_COUNT_COLOR
                ),
                HudSetting.slider(MAX_MOBS_KEY, "Tracked Mobs",
                        1f, 4f, 1f,
                        () -> true,
                        () -> WidgetConfigManager.getFloat(w, MAX_MOBS_KEY, DEFAULT_MAX_MOBS),
                        value -> {
                            WidgetConfigManager.setFloat(w, MAX_MOBS_KEY, (float) value, true);
                            trimEntries();
                            saveEntries();
                        },
                        DEFAULT_MAX_MOBS
                ),
                HudSetting.slider(EXPIRY_SECONDS_KEY, "Delay",
                        0f, 300f, 5f,
                        () -> true,
                        () -> WidgetConfigManager.getFloat(w, EXPIRY_SECONDS_KEY, DEFAULT_EXPIRY_SECONDS),
                        value -> {
                            WidgetConfigManager.setFloat(w, EXPIRY_SECONDS_KEY, (float) value, true);
                            if (purgeExpiredEntries(System.currentTimeMillis())) {
                                saveEntries();
                            }
                        },
                        DEFAULT_EXPIRY_SECONDS
                ),
                HudSetting.toggle(RANGED_MAGIC_KEY, "Ranged/Magic",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, RANGED_MAGIC_KEY, true),
                        b -> {
                            WidgetConfigManager.setBool(w, RANGED_MAGIC_KEY, b, true);
                            if (!b) {
                                recentRangedCandidates.clear();
                            }
                        },
                        true
                ),
                HudSetting.toggle(RESET_ON_RESTART_KEY, "Reset on Restart",
                        () -> true,
                        () -> WidgetConfigManager.getBool(w, RESET_ON_RESTART_KEY, false),
                        b -> WidgetConfigManager.setBool(w, RESET_ON_RESTART_KEY, b, true),
                        false
                ),
                HudSetting.section("Filters"),
                HudSetting.toggle(BLACKLIST_TOGGLE_KEY, "Enable blacklist",
                        () -> !WidgetConfigManager.getBool(w, WHITELIST_TOGGLE_KEY, false),
                        () -> WidgetConfigManager.getBool(w, BLACKLIST_TOGGLE_KEY, false),
                        enabled -> setFilterMode(BLACKLIST_TOGGLE_KEY, WHITELIST_TOGGLE_KEY, enabled),
                        false
                ),
                HudSetting.customList(
                        BLACKLIST_KEY,
                        "Blacklist",
                        () -> WidgetConfigManager.getBool(w, BLACKLIST_TOGGLE_KEY, false),
                        new CustomListSpec(
                                List.of(CustomField.text("mob", "Mob", "Name or id", true, 80)),
                                () -> getMobFilterEntries(BLACKLIST_KEY),
                                entries -> setMobFilterEntries(BLACKLIST_KEY, entries),
                                entry -> entry.get("mob"),
                                entry -> ""
                        )
                ),
                HudSetting.toggle(WHITELIST_TOGGLE_KEY, "Enable whitelist",
                        () -> !WidgetConfigManager.getBool(w, BLACKLIST_TOGGLE_KEY, false),
                        () -> WidgetConfigManager.getBool(w, WHITELIST_TOGGLE_KEY, false),
                        enabled -> setFilterMode(WHITELIST_TOGGLE_KEY, BLACKLIST_TOGGLE_KEY, enabled),
                        false
                ),
                HudSetting.customList(
                        WHITELIST_KEY,
                        "Whitelist",
                        () -> WidgetConfigManager.getBool(w, WHITELIST_TOGGLE_KEY, false),
                        new CustomListSpec(
                                List.of(CustomField.text("mob", "Mob", "Name or id", true, 80)),
                                () -> getMobFilterEntries(WHITELIST_KEY),
                                entries -> setMobFilterEntries(WHITELIST_KEY, entries),
                                entry -> entry.get("mob"),
                                entry -> ""
                        )
                )
        );
    }

    private int getRowsWidth(TextRenderer textRenderer, List<MobEntry> rows) {
        int maxTextWidth = 0;
        for (MobEntry entry : rows) {
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth("x" + Math.max(0, entry.count) + " kills"));
        }
        return ENTITY_BOX + TEXT_GAP + maxTextWidth;
    }

    private int getRowsHeight(List<MobEntry> rows) {
        int rowCount = Math.max(1, rows.size());
        return rowCount * ENTITY_BOX + Math.max(0, rowCount - 1) * ROW_GAP;
    }

    private int getRenderTop(int height) {
        return (int) y - Math.max(0, height - ENTITY_BOX);
    }

    private boolean usesDecoratedBounds() {
        return WidgetConfigManager.getBool(getName(), "bgToggle", true)
                || WidgetConfigManager.getBool(getName(), "brdToggle", true);
    }

    private int getMaxMobs() {
        float maxMobs = WidgetConfigManager.getFloat(getName(), MAX_MOBS_KEY, DEFAULT_MAX_MOBS);
        return Math.max(1, Math.min(4, Math.round(maxMobs)));
    }

    private int getExpirySeconds() {
        float seconds = WidgetConfigManager.getFloat(getName(), EXPIRY_SECONDS_KEY, DEFAULT_EXPIRY_SECONDS);
        return Math.max(0, Math.min(300, Math.round(seconds)));
    }

    private boolean isRangedMagicAttributionEnabled() {
        return WidgetConfigManager.getBool(getName(), RANGED_MAGIC_KEY, true);
    }

    private void setFilterMode(String enabledKey, String disabledKey, boolean enabled) {
        WidgetConfigManager.setBool(getName(), enabledKey, enabled, false);
        if (enabled) {
            WidgetConfigManager.setBool(getName(), disabledKey, false, false);
        }
        WidgetConfigManager.save();
    }

    private void normalizeFilterMode() {
        boolean blacklistEnabled = WidgetConfigManager.getBool(getName(), BLACKLIST_TOGGLE_KEY, false);
        boolean whitelistEnabled = WidgetConfigManager.getBool(getName(), WHITELIST_TOGGLE_KEY, false);
        if (blacklistEnabled && whitelistEnabled) {
            WidgetConfigManager.setBool(getName(), WHITELIST_TOGGLE_KEY, false, true);
        }
    }

    private boolean shouldTrackMob(MobSnapshot snapshot, String safeName) {
        boolean whitelistEnabled = WidgetConfigManager.getBool(getName(), WHITELIST_TOGGLE_KEY, false);
        boolean blacklistEnabled = WidgetConfigManager.getBool(getName(), BLACKLIST_TOGGLE_KEY, false);

        if (whitelistEnabled) {
            return matchesAnyMobFilter(snapshot, safeName, WHITELIST_KEY);
        }
        if (blacklistEnabled) {
            return !matchesAnyMobFilter(snapshot, safeName, BLACKLIST_KEY);
        }
        return true;
    }

    private boolean matchesAnyMobFilter(MobSnapshot snapshot, String safeName, String key) {
        Set<String> names = mobFilterNames(snapshot, safeName);
        for (MobFilter filter : WidgetConfigManager.getObjectList(getName(), key, MobFilter.class)) {
            if (filter == null || filter.mob == null || filter.mob.isBlank()) {
                continue;
            }

            if (names.contains(normalizeFilterName(filter.mob))) {
                return true;
            }
        }
        return false;
    }

    private List<CustomEntry> getMobFilterEntries(String key) {
        List<CustomEntry> entries = new ArrayList<>();
        for (MobFilter filter : WidgetConfigManager.getObjectList(getName(), key, MobFilter.class)) {
            if (filter == null || filter.mob == null || filter.mob.isBlank()) {
                continue;
            }

            Map<String, String> values = new LinkedHashMap<>();
            values.put("mob", filter.mob);
            entries.add(new CustomEntry(values));
        }
        return List.copyOf(entries);
    }

    private void setMobFilterEntries(String key, List<CustomEntry> entries) {
        List<MobFilter> filters = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CustomEntry entry : entries) {
            String mob = entry.get("mob").trim();
            String normalized = normalizeFilterName(mob);
            if (!mob.isBlank() && seen.add(normalized)) {
                filters.add(new MobFilter(mob));
            }
        }
        WidgetConfigManager.setObjectList(getName(), key, filters, true);
    }

    private MobEntity findAimedMob(MinecraftClient client) {
        Vec3d start = client.player.getEyePos();
        Vec3d end = start.add(client.player.getRotationVec(1f).normalize().multiply(AIM_RANGE));

        MobEntity closest = null;
        double closestDistanceSq = AIM_RANGE * AIM_RANGE;
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof MobEntity mob) || !mob.isAlive()) {
                continue;
            }

            var hit = mob.getBoundingBox().expand(0.35d).raycast(start, end);
            if (hit.isEmpty()) {
                continue;
            }

            double distanceSq = start.squaredDistanceTo(hit.get());
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = mob;
            }
        }
        return closest;
    }

    private MobEntity findNearestMobToProjectile(MinecraftClient client, ProjectileEntity projectile) {
        MobEntity closest = null;
        double closestDistanceSq = PROJECTILE_TARGET_DISTANCE_SQ;
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof MobEntity mob) || !mob.isAlive()) {
                continue;
            }

            double distanceSq = mob.getBoundingBox().expand(0.6d).squaredMagnitude(projectile.getEntityPos());
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = mob;
            }
        }
        return closest;
    }

    private boolean isPlayerProjectileCandidate(ProjectileEntity projectile, PlayerEntity player, long nowTick) {
        Entity owner = projectile.getOwner();
        if (owner != null && owner.getUuid().equals(player.getUuid())) {
            possiblePlayerProjectiles.put(projectile.getId(), nowTick);
            return true;
        }

        if (possiblePlayerProjectiles.containsKey(projectile.getId())) {
            possiblePlayerProjectiles.put(projectile.getId(), nowTick);
            return true;
        }

        if (!isInRecentMagicActionWindow(nowTick)
                || projectile.age > MAGIC_ACTION_WINDOW_TICKS
                || projectile.squaredDistanceTo(player) > PROJECTILE_SPAWN_DISTANCE_SQ) {
            return false;
        }

        Vec3d velocity = projectile.getVelocity();
        Vec3d look = player.getRotationVec(1f).normalize();
        Vec3d toProjectile = projectile.getEntityPos().subtract(player.getEyePos()).normalize();
        boolean plausible = velocity.lengthSquared() < 0.001d
                || look.dotProduct(velocity.normalize()) > 0.35d
                || look.dotProduct(toProjectile) > 0.15d;
        if (plausible) {
            possiblePlayerProjectiles.put(projectile.getId(), nowTick);
        }
        return plausible;
    }

    private boolean isOtherPlayerProjectileCandidate(ProjectileEntity projectile, MinecraftClient client, long nowTick) {
        Entity owner = projectile.getOwner();
        if (owner instanceof PlayerEntity player && !player.getUuid().equals(client.player.getUuid())) {
            possibleOtherPlayerProjectiles.put(projectile.getId(), nowTick);
            return true;
        }

        if (possibleOtherPlayerProjectiles.containsKey(projectile.getId())) {
            possibleOtherPlayerProjectiles.put(projectile.getId(), nowTick);
            return true;
        }

        if (projectile.age > MAGIC_ACTION_WINDOW_TICKS) {
            return false;
        }

        PlayerEntity nearbyOtherPlayer = findNearbyOtherPlayerForProjectile(projectile, client);
        if (nearbyOtherPlayer == null) {
            return false;
        }

        Vec3d velocity = projectile.getVelocity();
        Vec3d look = nearbyOtherPlayer.getRotationVec(1f).normalize();
        Vec3d toProjectile = projectile.getEntityPos().subtract(nearbyOtherPlayer.getEyePos()).normalize();
        boolean plausible = velocity.lengthSquared() < 0.001d
                || look.dotProduct(velocity.normalize()) > 0.35d
                || look.dotProduct(toProjectile) > 0.15d;
        if (plausible) {
            possibleOtherPlayerProjectiles.put(projectile.getId(), nowTick);
        }
        return plausible;
    }

    private PlayerEntity findNearbyOtherPlayerForProjectile(ProjectileEntity projectile, MinecraftClient client) {
        PlayerEntity closest = null;
        double closestDistanceSq = PROJECTILE_SPAWN_DISTANCE_SQ;
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player.getUuid().equals(client.player.getUuid())) {
                continue;
            }

            double distanceSq = projectile.squaredDistanceTo(player);
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = player;
            }
        }
        return closest;
    }

    private boolean isBlockedRangedTarget(int entityId, long nowTick) {
        Long blockedTick = blockedRangedTargets.get(entityId);
        return blockedTick != null && nowTick - blockedTick <= OTHER_PROJECTILE_BLOCK_TICKS;
    }

    private boolean hasCompetingOtherPlayer(MinecraftClient client, LivingEntity mob) {
        double selfDistanceSq = mob.squaredDistanceTo(client.player);
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player.getUuid().equals(client.player.getUuid())) {
                continue;
            }

            double otherDistanceSq = mob.squaredDistanceTo(player);
            if (otherDistanceSq <= OTHER_PLAYER_CONTEST_RADIUS_SQ && otherDistanceSq <= selfDistanceSq * 1.35d) {
                return true;
            }
        }
        return false;
    }

    private boolean isInRecentMagicActionWindow(long nowTick) {
        return nowTick - lastMagicActionTick <= MAGIC_ACTION_WINDOW_TICKS;
    }

    private static boolean isHoldingItem(PlayerEntity player) {
        return !player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty();
    }

    private static List<MobEntry> placeholderRows() {
        return List.of(new MobEntry(
                "minecraft:zombie|zombie",
                "minecraft:zombie",
                "Zombie",
                List.of(),
                0,
                System.currentTimeMillis()
        ));
    }

    private static boolean isValidEntry(MobEntry entry) {
        return entry != null
                && entry.typeId != null
                && !entry.typeId.isBlank()
                && entry.name != null
                && !entry.name.isBlank()
                && entry.count >= 0;
    }

    private static MobSnapshot snapshotMob(LivingEntity entity) {
        String typeId = EntityType.getId(entity.getType()).toString();
        String displayName = entity.getDisplayName().getString();
        return new MobSnapshot(typeId, displayName, snapshotEquipment(entity));
    }

    private static List<EquipmentSnapshot> snapshotEquipment(LivingEntity entity) {
        List<EquipmentSnapshot> equipment = new ArrayList<>();
        for (EquipmentSlot slot : FINGERPRINT_EQUIPMENT_SLOTS) {
            try {
                ItemStack stack = entity.getEquippedStack(slot);
                if (stack.isEmpty()) {
                    continue;
                }

                equipment.add(new EquipmentSnapshot(
                        slot.getName(),
                        Registries.ITEM.getId(stack.getItem()).toString(),
                        stack.getName().getString(),
                        componentFingerprint(stack),
                        stack.copyWithCount(1)
                ));
            } catch (RuntimeException ignored) {
            }
        }
        return List.copyOf(equipment);
    }

    private static String entryKey(MobSnapshot snapshot) {
        String normalizedName = normalizeMobNameKey(snapshot.displayName);
        StringBuilder key = new StringBuilder(snapshot.typeId).append("|").append(normalizedName);
        for (EquipmentSnapshot equipment : sanitizeEquipment(snapshot.equipment)) {
            key.append("|")
                    .append(normalizeKeyPart(equipment.slot))
                    .append("=")
                    .append(normalizeKeyPart(equipment.itemId))
                    .append("@")
                    .append(normalizeKeyPart(equipment.components));
        }
        return key.toString();
    }

    private static List<EquipmentSnapshot> sanitizeEquipment(List<EquipmentSnapshot> equipment) {
        if (equipment == null || equipment.isEmpty()) {
            return List.of();
        }

        List<EquipmentSnapshot> sanitized = new ArrayList<>();
        for (EquipmentSnapshot snapshot : equipment) {
            if (snapshot == null || snapshot.slot == null || snapshot.slot.isBlank()
                    || snapshot.itemId == null || snapshot.itemId.isBlank()) {
                continue;
            }
            sanitized.add(new EquipmentSnapshot(
                    snapshot.slot,
                    snapshot.itemId,
                    snapshot.name == null ? "" : snapshot.name,
                    snapshot.components == null ? "" : snapshot.components,
                    snapshot.stack == null || snapshot.stack.isEmpty() ? null : snapshot.stack.copyWithCount(1)
            ));
        }
        return List.copyOf(sanitized);
    }

    private static String componentFingerprint(ItemStack stack) {
        try {
            return stack.getComponentChanges()
                    .withRemovedIf(MobKillTracker::isIgnoredEquipmentComponent)
                    .toString();
        } catch (RuntimeException ignored) {
            return stack.getComponents().toString();
        }
    }

    private static boolean isIgnoredEquipmentComponent(ComponentType<?> type) {
        return type == DataComponentTypes.DAMAGE;
    }

    private static String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static Set<String> mobFilterNames(MobSnapshot snapshot, String safeName) {
        Set<String> names = new HashSet<>();
        String typeId = snapshot == null ? "" : snapshot.typeId;
        if (typeId != null && !typeId.isBlank()) {
            names.add(normalizeFilterName(typeId));
            int colon = typeId.indexOf(':');
            if (colon >= 0 && colon + 1 < typeId.length()) {
                names.add(normalizeFilterName(typeId.substring(colon + 1)));
            }
        }

        names.add(normalizeFilterName(safeName));
        if (snapshot != null) {
            names.add(normalizeFilterName(snapshot.displayName));
            names.add(normalizeMobNameKey(snapshot.displayName));
        }
        return names;
    }

    private static String normalizeFilterName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String normalizeMobNameKey(String value) {
        String normalized = value == null ? "" : value;
        normalized = HEART_HEALTH_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = HEALTH_WORD_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = HEALTH_SUFFIX_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = normalized
                .replaceAll("(?i)\\b\\d+(?:[.,]\\d+)?\\s*%\\s*(?:hp|health)?\\b", " ")
                .replaceAll("\\s*[-|•]\\s*$", " ");
        return normalizeKeyPart(normalized);
    }

    private static String fallbackInitial(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        return name.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private record RecentAttack(int entityId, UUID uuid, MobSnapshot snapshot, long attackTick) {
    }

    private record RangedCandidate(MobSnapshot snapshot, long tick) {
    }

    private record MobSnapshot(String typeId, String displayName, List<EquipmentSnapshot> equipment) {
    }

    public static final class EquipmentSnapshot {
        public String slot;
        public String itemId;
        public String name;
        public String components;
        public transient ItemStack stack;

        public EquipmentSnapshot() {
        }

        private EquipmentSnapshot(String slot, String itemId, String name, String components, ItemStack stack) {
            this.slot = slot;
            this.itemId = itemId;
            this.name = name;
            this.components = components;
            this.stack = stack;
        }
    }

    public static final class MobEntry {
        public String key;
        public String typeId;
        public String name;
        public List<EquipmentSnapshot> equipment = List.of();
        public int count;
        public long lastUpdatedEpochMs;
        public transient LivingEntity previewEntity;
        public transient boolean renderFailed;

        public MobEntry() {
        }

        private MobEntry(String key, String typeId, String name, List<EquipmentSnapshot> equipment, int count, long lastUpdatedEpochMs) {
            this.key = key;
            this.typeId = typeId;
            this.name = name;
            this.equipment = sanitizeEquipment(equipment);
            this.count = count;
            this.lastUpdatedEpochMs = lastUpdatedEpochMs;
        }
    }

    private static final class MobFilter {
        public String mob;

        public MobFilter() {
        }

        private MobFilter(String mob) {
            this.mob = mob;
        }
    }
}
