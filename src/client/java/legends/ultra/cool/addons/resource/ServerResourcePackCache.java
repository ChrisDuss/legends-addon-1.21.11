package legends.ultra.cool.addons.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import legends.ultra.cool.addons.LegendsAddon;
import legends.ultra.cool.addons.data.AddonConfigPaths;
import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.util.AddonServerGate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.server.ServerResourcePackLoader;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ServerResourcePackCache {
    private static final Pattern SHA1 = Pattern.compile("^[a-fA-F0-9]{40}$");
    private static final UUID DEFAULT_LEGENDS_PACK_ID = UUID.fromString("7bf83c39-71c8-3960-8c3e-8b1697728081");
    private static final String DEFAULT_LEGENDS_PACK_HASH = "18447a0f82921ce3967c34a1a0b20478be98b56c";
    private static final String CONFIG_FILE = "legends-addon-server-resource-pack.properties";
    private static final String GENERAL_SETTINGS_CONFIG_ID = "__general_settings__";
    private static final String ENABLED_KEY = "serverResourcePackCacheEnabled";
    private static final Map<UUID, String> PENDING_HASHES = new ConcurrentHashMap<>();
    private static final Set<UUID> SERVER_PACK_IDS = ConcurrentHashMap.newKeySet();

    private static boolean preloadAttempted;
    private static boolean serverRequestedRemoval;
    private static UUID activeId;
    private static String activeHash;

    private ServerResourcePackCache() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (preloadAttempted) {
                return;
            }
            preloadAttempted = true;
            if (!isEnabled()) {
                return;
            }
            preloadBestCachedPack(client);
        });
    }

    public static boolean tryHandle(ResourcePackSendS2CPacket packet, MinecraftClient client, ClientConnection connection) {
        if (!isEnabled()) {
            return false;
        }

        String hash = normalizeHash(packet.hash());
        if (hash == null) {
            return false;
        }

        UUID id = packet.id();
        Optional<Candidate> savedPreloadCandidate = savedPreloadCandidate(client);
        if (savedPreloadCandidate.isPresent() && savedPreloadCandidate.get().hash().equals(hash)) {
            markActive(client, savedPreloadCandidate.get().id(), hash);
            sendLoaded(connection, id);
            return true;
        }

        if (hash.equals(activeHash)) {
            sendLoaded(connection, id);
            return true;
        }

        if (!AddonServerGate.isOnLegendsServer()) {
            return false;
        }

        PENDING_HASHES.put(id, hash);

        Path cachedPack = cachedPackPath(client, id, hash);
        if (!isVerifiedPack(cachedPack, hash)) {
            return false;
        }

        loadCachedPack(client, id, hash, cachedPack, true);
        connection.send(new ResourcePackStatusC2SPacket(id, ResourcePackStatusC2SPacket.Status.DOWNLOADED));
        return true;
    }

    public static void onPackRequested(UUID id) {
        if (id != null) {
            SERVER_PACK_IDS.add(id);
        }
    }

    public static void onStatusSent(ResourcePackStatusC2SPacket packet) {
        UUID id = packet.id();
        ResourcePackStatusC2SPacket.Status status = packet.status();
        String hash = PENDING_HASHES.get(id);

        if (status == ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED && hash != null) {
            markActive(MinecraftClient.getInstance(), id, hash);
            PENDING_HASHES.remove(id);
            return;
        }

        if (status.hasFinished()) {
            PENDING_HASHES.remove(id);
        }
    }

    public static void onPackRemoved(UUID id) {
        if (id.equals(activeId)) {
            activeId = null;
            activeHash = null;
        }
        SERVER_PACK_IDS.remove(id);
        PENDING_HASHES.remove(id);
    }

    public static void onAllPacksRemoved() {
        activeId = null;
        activeHash = null;
        PENDING_HASHES.clear();
        SERVER_PACK_IDS.clear();
    }

    public static void beginServerRequestedRemoval() {
        serverRequestedRemoval = true;
    }

    public static void endServerRequestedRemoval() {
        serverRequestedRemoval = false;
    }

    public static boolean shouldPreserveOnClientDisconnect() {
        return isEnabled() && !serverRequestedRemoval && activeHash != null;
    }

    public static Set<UUID> packsToRemoveOnClientDisconnect() {
        UUID preservedId = activeId;
        if (preservedId == null) {
            return Set.copyOf(SERVER_PACK_IDS);
        }

        return SERVER_PACK_IDS.stream()
                .filter(id -> !preservedId.equals(id))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static boolean isEnabled() {
        return WidgetConfigManager.getBool(GENERAL_SETTINGS_CONFIG_ID, ENABLED_KEY, true);
    }

    public static void setEnabled(boolean enabled) {
        WidgetConfigManager.setBool(GENERAL_SETTINGS_CONFIG_ID, ENABLED_KEY, enabled, true);
        if (!enabled) {
            activeId = null;
            activeHash = null;
            PENDING_HASHES.clear();
            SERVER_PACK_IDS.clear();
        }
    }

    private static void preloadBestCachedPack(MinecraftClient client) {
        Optional<Candidate> candidate = savedPreloadCandidate(client);

        if (candidate.isEmpty() && AddonServerGate.isOnLegendsServer()) {
            candidate = latestLogCandidate(client);
        }

        candidate.ifPresent(pack -> {
            Path path = cachedPackPath(client, pack.id(), pack.hash());
            if (isVerifiedPack(path, pack.hash())) {
                loadCachedPack(client, pack.id(), pack.hash(), path, false);
            }
        });
    }

    private static Optional<Candidate> savedPreloadCandidate(MinecraftClient client) {
        return savedCandidate(client)
                .or(() -> verifiedDefaultCandidate(client));
    }

    private static void loadCachedPack(MinecraftClient client, UUID id, String hash, Path path, boolean forServerJoin) {
        ServerResourcePackLoader loader = client.getServerResourcePackProvider();
        CompletableFuture<Void> loadFuture = loader.getPackLoadFuture(id);

        loader.addResourcePack(id, path);
        loader.acceptAll();

        loadFuture.whenComplete((ignored, throwable) -> client.execute(() -> {
            if (throwable == null) {
                markActive(client, id, hash);
                if (forServerJoin) {
                    LegendsAddon.LOGGER.info("Loaded cached server resource pack {}", hash);
                }
            } else {
                LegendsAddon.LOGGER.warn("Failed to load cached server resource pack {}", hash, throwable);
            }
        }));
    }

    private static void sendLoaded(ClientConnection connection, UUID id) {
        connection.send(new ResourcePackStatusC2SPacket(id, ResourcePackStatusC2SPacket.Status.ACCEPTED));
        connection.send(new ResourcePackStatusC2SPacket(id, ResourcePackStatusC2SPacket.Status.DOWNLOADED));
        connection.send(new ResourcePackStatusC2SPacket(id, ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
    }

    private static void markActive(MinecraftClient client, UUID id, String hash) {
        activeId = id;
        activeHash = hash;
        SERVER_PACK_IDS.add(id);
        saveCandidate(client, new Candidate(id, hash));
    }

    private static Optional<Candidate> savedCandidate(MinecraftClient client) {
        Path config = configPath(client);
        if (!Files.isRegularFile(config)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(config)) {
            properties.load(in);
            UUID id = UUID.fromString(properties.getProperty("id", ""));
            String hash = normalizeHash(properties.getProperty("hash", ""));
            if (hash != null) {
                return Optional.of(new Candidate(id, hash));
            }
        } catch (IllegalArgumentException | IOException ignored) {
        }

        return Optional.empty();
    }

    private static Optional<Candidate> verifiedDefaultCandidate(MinecraftClient client) {
        Candidate candidate = new Candidate(DEFAULT_LEGENDS_PACK_ID, DEFAULT_LEGENDS_PACK_HASH);
        return isVerifiedPack(cachedPackPath(client, candidate.id(), candidate.hash()), candidate.hash())
                ? Optional.of(candidate)
                : Optional.empty();
    }

    private static Optional<Candidate> latestLogCandidate(MinecraftClient client) {
        Path log = client.runDirectory.toPath().resolve("downloads").resolve("log.json");
        if (!Files.isRegularFile(log)) {
            return Optional.empty();
        }

        Candidate latest = null;
        try (BufferedReader reader = Files.newBufferedReader(log)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Candidate candidate = parseLogCandidate(line);
                if (candidate != null && isVerifiedPack(cachedPackPath(client, candidate.id(), candidate.hash()), candidate.hash())) {
                    latest = candidate;
                }
            }
        } catch (IOException ignored) {
        }

        return Optional.ofNullable(latest);
    }

    private static Candidate parseLogCandidate(String line) {
        try {
            JsonObject object = JsonParser.parseString(line).getAsJsonObject();
            String url = object.get("url").getAsString();
            if (url.contains("minehut-ads-resource-packs")) {
                return null;
            }

            UUID id = UUID.fromString(object.get("id").getAsString());
            String hash = normalizeHash(object.get("hash").getAsString());
            return hash == null ? null : new Candidate(id, hash);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void saveCandidate(MinecraftClient client, Candidate candidate) {
        Path config = configPath(client);
        Properties properties = new Properties();
        properties.setProperty("id", candidate.id().toString());
        properties.setProperty("hash", candidate.hash());

        try {
            Files.createDirectories(config.getParent());
            try (OutputStream out = Files.newOutputStream(config)) {
                properties.store(out, "Last successfully loaded server resource pack");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path configPath(MinecraftClient client) {
        return AddonConfigPaths.configFile(client, CONFIG_FILE);
    }

    private static Path cachedPackPath(MinecraftClient client, UUID id, String hash) {
        return client.runDirectory.toPath()
                .resolve("downloads")
                .resolve(id.toString())
                .resolve(hash);
    }

    private static String normalizeHash(String hash) {
        if (hash == null || !SHA1.matcher(hash).matches()) {
            return null;
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    private static boolean isVerifiedPack(Path path, String expectedHash) {
        if (!Files.isRegularFile(path)) {
            return false;
        }

        try {
            return expectedHash.equals(sha1(path));
        } catch (IOException | NoSuchAlgorithmException ignored) {
            return false;
        }
    }

    private static String sha1(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] buffer = new byte[8192];

        try (InputStream in = Files.newInputStream(path)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        StringBuilder result = new StringBuilder(40);
        for (byte b : digest.digest()) {
            result.append(String.format(Locale.ROOT, "%02x", b));
        }
        return result.toString();
    }

    private record Candidate(UUID id, String hash) {
    }
}
