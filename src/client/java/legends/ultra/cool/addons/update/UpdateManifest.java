package legends.ultra.cool.addons.update;

public record UpdateManifest(
        String version,
        String minecraftVersion,
        String jarName,
        String downloadUrl,
        String sha256,
        String releasePage
) {
}
