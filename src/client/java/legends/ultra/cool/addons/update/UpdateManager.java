package legends.ultra.cool.addons.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import legends.ultra.cool.addons.LegendsAddon;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public final class UpdateManager {
    private static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/ChrisDuss/legends-addon-1.21.11/main/update-1.21.11.json";
    private static final String MOD_JAR_PREFIX = "legends-addon";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile State state = State.NOT_CHECKED;
    private static volatile UpdateManifest availableManifest;
    private static volatile String statusMessage = "Not checked";

    private UpdateManager() {
    }

    public static void init() {
        checkForUpdates();
    }

    public static void checkForUpdates() {
        if (state == State.CHECKING || state == State.DOWNLOADING) {
            return;
        }

        state = State.CHECKING;
        statusMessage = "Checking for updates";

        HttpRequest request = HttpRequest.newBuilder(URI.create(MANIFEST_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(UpdateManager::handleManifestResponse)
                .exceptionally(throwable -> {
                    failCheck("Could not check for updates", throwable);
                    return null;
                });
    }

    public static Text getButtonText() {
        return switch (state) {
            case NOT_CHECKED -> Text.literal("Legends Addon: Check update");
            case CHECKING -> Text.literal("Legends Addon: Checking...");
            case UP_TO_DATE -> Text.literal("Legends Addon: Up to date");
            case AVAILABLE -> Text.literal("Update Legends Addon");
            case FAILED -> Text.literal("Legends Addon: Retry update check");
            case DOWNLOADING -> Text.literal("Legends Addon: Updating...");
            case INSTALLED -> Text.literal("Legends Addon: Restart required");
            case INSTALL_FAILED -> Text.literal("Legends Addon: Retry update");
        };
    }

    public static boolean isButtonActive() {
        return state == State.NOT_CHECKED
                || state == State.AVAILABLE
                || state == State.FAILED
                || state == State.INSTALL_FAILED;
    }

    public static boolean canDownloadUpdate() {
        return (state == State.AVAILABLE || state == State.INSTALL_FAILED) && availableManifest != null;
    }

    public static UpdateManifest getAvailableManifest() {
        return availableManifest;
    }

    public static String getInstalledVersion() {
        return FabricLoader.getInstance()
                .getModContainer(LegendsAddon.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

    public static String getStatusMessage() {
        return statusMessage;
    }

    public static void downloadAndInstall(MinecraftClient client) {
        UpdateManifest manifest = availableManifest;
        if (manifest == null || state == State.DOWNLOADING) {
            return;
        }

        String downloadUrl = safeTrim(manifest.downloadUrl());
        if (downloadUrl.isBlank()) {
            failInstall(client, "Release is missing a download URL", null);
            return;
        }

        state = State.DOWNLOADING;
        statusMessage = "Downloading " + manifest.version();

        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/java-archive,application/octet-stream,*/*")
                .GET()
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> {
                    try {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            throw new IOException("Download failed with HTTP " + response.statusCode());
                        }
                        installDownloadedJar(manifest, response.body());
                        state = State.INSTALLED;
                        statusMessage = "Installed " + manifest.version() + ". Restart Minecraft.";
                        showToast(client, "Legends Addon updated", "Restart Minecraft to finish.");
                    } catch (Exception exception) {
                        failInstall(client, "Could not install update", exception);
                    }
                })
                .exceptionally(throwable -> {
                    failInstall(client, "Could not download update", throwable);
                    return null;
                });
    }

    public static void openReleasePage() {
        UpdateManifest manifest = availableManifest;
        String url = manifest == null ? "" : safeTrim(manifest.releasePage());
        if (url.isBlank()) {
            url = "https://github.com/ChrisDuss/legends-addon-1.21.11/releases";
        }
        Util.getOperatingSystem().open(url);
    }

    private static void handleManifestResponse(HttpResponse<String> response) {
        try {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Update manifest failed with HTTP " + response.statusCode());
            }

            UpdateManifest manifest = parseManifest(response.body());
            String manifestMinecraftVersion = safeTrim(manifest.minecraftVersion());
            String currentMinecraftVersion = FabricLoader.getInstance().getRawGameVersion();
            if (!manifestMinecraftVersion.isBlank() && !manifestMinecraftVersion.equals(currentMinecraftVersion)) {
                availableManifest = null;
                state = State.UP_TO_DATE;
                statusMessage = "No update for Minecraft " + currentMinecraftVersion;
                return;
            }

            if (isRemoteVersionNewer(manifest.version())) {
                availableManifest = manifest;
                state = State.AVAILABLE;
                statusMessage = "Update " + manifest.version() + " available";
            } else {
                availableManifest = null;
                state = State.UP_TO_DATE;
                statusMessage = "Up to date";
            }
        } catch (Exception exception) {
            failCheck("Could not read update manifest", exception);
        }
    }

    private static UpdateManifest parseManifest(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return new UpdateManifest(
                getString(root, "version"),
                getString(root, "minecraftVersion"),
                getString(root, "jarName"),
                getString(root, "downloadUrl"),
                getString(root, "sha256"),
                getString(root, "releasePage")
        );
    }

    private static String getString(JsonObject root, String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return "";
        }
        return root.get(key).getAsString();
    }

    private static boolean isRemoteVersionNewer(String remoteVersion) throws VersionParsingException {
        String remote = safeTrim(remoteVersion);
        if (remote.isBlank()) {
            return false;
        }

        String installed = getInstalledVersion();
        try {
            return Version.parse(remote).compareTo(Version.parse(installed)) > 0;
        } catch (VersionParsingException exception) {
            return compareLooseVersions(remote, installed) > 0;
        }
    }

    private static int compareLooseVersions(String left, String right) {
        String[] leftParts = normalizeVersion(left).split("\\.");
        String[] rightParts = normalizeVersion(right).split("\\.");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < count; index++) {
            int leftValue = index < leftParts.length ? parseVersionPart(leftParts[index]) : 0;
            int rightValue = index < rightParts.length ? parseVersionPart(rightParts[index]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static String normalizeVersion(String version) {
        String normalized = version.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("v")) {
            normalized = normalized.substring(1);
        }
        return normalized.replaceAll("[^0-9]+", ".");
    }

    private static int parseVersionPart(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static void installDownloadedJar(UpdateManifest manifest, byte[] jarBytes) throws IOException {
        String jarName = safeJarName(manifest.jarName());
        verifySha256(manifest, jarBytes);

        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
        Files.createDirectories(modsDir);

        Path target = modsDir.resolve(jarName);
        Path temp = modsDir.resolve(jarName + ".tmp");
        Files.write(temp, jarBytes);

        List<MovedJar> movedJars = new ArrayList<>();
        try {
            for (Path oldJar : findActiveAddonJars(modsDir, target.getFileName().toString())) {
                Path disabledPath = nextDisabledPath(oldJar);
                Files.move(oldJar, disabledPath, StandardCopyOption.REPLACE_EXISTING);
                movedJars.add(new MovedJar(oldJar, disabledPath));
            }

            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            rollbackMovedJars(movedJars);
            Files.deleteIfExists(temp);
            throw exception;
        }
    }

    private static String safeJarName(String jarName) throws IOException {
        String safeName = safeTrim(jarName);
        if (safeName.isBlank() || !safeName.endsWith(".jar")) {
            throw new IOException("Manifest jarName must end with .jar");
        }

        Path fileName = Path.of(safeName).getFileName();
        if (fileName == null || !fileName.toString().equals(safeName)) {
            throw new IOException("Manifest jarName must be a plain file name");
        }
        return safeName;
    }

    private static void verifySha256(UpdateManifest manifest, byte[] jarBytes) throws IOException {
        String expected = safeTrim(manifest.sha256()).toLowerCase(Locale.ROOT);
        if (expected.isBlank()) {
            return;
        }

        String actual;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            actual = HexFormat.of().formatHex(digest.digest(jarBytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is not available", exception);
        }

        if (!actual.equals(expected)) {
            throw new IOException("Downloaded jar SHA-256 did not match manifest");
        }
    }

    private static List<Path> findActiveAddonJars(Path modsDir, String newJarName) throws IOException {
        List<Path> jars = new ArrayList<>();
        if (!Files.isDirectory(modsDir)) {
            return jars;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, MOD_JAR_PREFIX + "*.jar")) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                if (!path.getFileName().toString().equals(newJarName)) {
                    jars.add(path);
                }
            }
        }
        return jars;
    }

    private static Path nextDisabledPath(Path jarPath) {
        Path disabledPath = jarPath.resolveSibling(jarPath.getFileName() + ".disabled");
        int suffix = 1;
        while (Files.exists(disabledPath)) {
            disabledPath = jarPath.resolveSibling(jarPath.getFileName() + ".disabled." + suffix);
            suffix++;
        }
        return disabledPath;
    }

    private static void rollbackMovedJars(List<MovedJar> movedJars) {
        for (int index = movedJars.size() - 1; index >= 0; index--) {
            MovedJar movedJar = movedJars.get(index);
            try {
                if (Files.exists(movedJar.disabledPath())) {
                    Files.move(movedJar.disabledPath(), movedJar.originalPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                LegendsAddon.LOGGER.warn("Could not restore disabled mod jar {}", movedJar.originalPath(), exception);
            }
        }
    }

    private static void failCheck(String message, Throwable throwable) {
        availableManifest = null;
        state = State.FAILED;
        statusMessage = message;
        LegendsAddon.LOGGER.warn(message, throwable);
    }

    private static void failInstall(MinecraftClient client, String message, Throwable throwable) {
        state = State.INSTALL_FAILED;
        String detail = throwable == null ? "" : safeTrim(throwable.getMessage());
        statusMessage = detail.isBlank() ? message : message + ": " + detail;
        if (throwable != null) {
            LegendsAddon.LOGGER.warn(message, throwable);
        }
        showToast(client, "Legends Addon update failed", statusMessage);
    }

    private static void showToast(MinecraftClient client, String title, String description) {
        if (client == null) {
            return;
        }

        client.execute(() -> SystemToast.show(
                client.getToastManager(),
                SystemToast.Type.PERIODIC_NOTIFICATION,
                Text.literal(title),
                Text.literal(description)
        ));
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public enum State {
        NOT_CHECKED,
        CHECKING,
        UP_TO_DATE,
        AVAILABLE,
        FAILED,
        DOWNLOADING,
        INSTALLED,
        INSTALL_FAILED
    }

    private record MovedJar(Path originalPath, Path disabledPath) {
    }
}
