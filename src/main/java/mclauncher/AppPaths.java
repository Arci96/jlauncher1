package mclauncher;

import java.nio.file.Path;

/** Zentrale Verzeichnisstruktur. Libraries/Assets/Versions werden von allen Instanzen geteilt. */
public final class AppPaths {
    public static final Path ROOT = root();
    public static final Path VERSIONS = ROOT.resolve("versions");
    public static final Path LIBRARIES = ROOT.resolve("libraries");
    public static final Path ASSETS = ROOT.resolve("assets");
    public static final Path INSTANCES = ROOT.resolve("instances");
    public static final Path CACHE = ROOT.resolve("cache");
    public static final Path CONFIG = ROOT.resolve("launcher.json");

    private AppPaths() {}

    private static Path root() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Path.of(appData != null ? appData : home, ".jlauncher");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "jlauncher");
        }
        return Path.of(home, ".local", "share", "jlauncher");
    }
}
