package mclauncher;

import java.nio.file.Path;
import java.util.UUID;

/** Ein Profil (Instanz): eigene Welten, Mods, Einstellungen. */
public class Instance {
    public String id = UUID.randomUUID().toString().substring(0, 8);
    public String name = "Neues Profil";
    public String mcVersion = "1.21.1";
    public String loader = "VANILLA";      // VANILLA, FABRIC, QUILT, FORGE, NEOFORGE
    public String loaderVersion = "";
    public String versionId;               // tatsächlich gestartete Version (nach Loader-Installation)
    public int ramMin = 1024;
    public int ramMax = 4096;
    public String javaPath = "";           // leer = automatisch
    public String jvmArgs = "";
    public String gameDirOverride = "";    // leer = Standardordner
    public long lastPlayed;

    public boolean hasCustomDir() { return gameDirOverride != null && !gameDirOverride.isBlank(); }

    public Path dir() {
        return hasCustomDir() ? Path.of(gameDirOverride) : AppPaths.INSTANCES.resolve(id);
    }

    public Path contentDir(ContentType t) { return dir().resolve(t.folder); }
    public Path modsDir() { return contentDir(ContentType.MOD); }
    public Path screenshotsDir() { return dir().resolve("screenshots"); }

    public Loaders.Type type() {
        try {
            return Loaders.Type.valueOf(loader);
        } catch (Exception e) {
            return Loaders.Type.VANILLA;
        }
    }

    public String label() {
        Loaders.Type t = type();
        return mcVersion + (t == Loaders.Type.VANILLA ? "" : " · " + t.label);
    }

    @Override
    public String toString() { return name; }
}
