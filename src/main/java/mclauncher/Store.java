package mclauncher;

import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Speichert Launcher-Einstellungen, Profile und Server in launcher.json. */
public final class Store {
    private Store() {}

    public static class Config {
        public String accountType = "offline";   // "offline" oder "microsoft"
        public String offlineName = "Player";
        public String msClientId = "";
        public String msRefreshToken = "";
        public String msName = "";
        public String msUuid = "";
        public String lastProfile = "";
        public int defaultRamMin = 1024;
        public int defaultRamMax = 4096;
        public String defaultJavaPath = "";
        public boolean minimizeOnLaunch = true;
        public int defaultFpsLimit = 0;
        public String curseForgeKey = "";
        public String msSkinUrl = "";
        public boolean msTokenSecure = false;    // Token liegt im Schlüsselbund statt in dieser Datei
        public boolean checkUpdates = true;
        public String updateRepo = "";           // GitHub: Benutzer/Repository
        public long lastUpdateCheck = 0;
        public String skippedVersion = "";
        public String heroScene = "SUNSET";
        public String heroImage = "";
        public String language = "";   // "" = nach Systemsprache, sonst "de" oder "en"
    }

    public static class ServerEntry {
        public String name = "";
        public String address = "";
        public ServerEntry() {}
        public ServerEntry(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }

    public static class Data {
        public Config config = new Config();
        public List<Instance> instances = new ArrayList<>();
        public List<ServerEntry> servers = new ArrayList<>();
    }

    public static Data data = new Data();

    public static synchronized void load() {
        try {
            if (Files.exists(AppPaths.CONFIG)) {
                String json = Files.readString(AppPaths.CONFIG, StandardCharsets.UTF_8);
                Data d = Util.GSON.fromJson(json, Data.class);
                if (d != null) {
                    if (d.config == null) d.config = new Config();
                    if (d.instances == null) d.instances = new ArrayList<>();
                    if (d.servers == null) d.servers = new ArrayList<>();
                    data = d;
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            System.err.println(I18n.t("Konfiguration konnte nicht geladen werden: ") + e);
            // Defekte Datei sichern, statt sie beim nächsten Speichern stillschweigend zu überschreiben
            try {
                Files.copy(AppPaths.CONFIG, AppPaths.CONFIG.resolveSibling("launcher.json.defekt"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) { }
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(AppPaths.ROOT);
            // Erst in eine Hilfsdatei schreiben, dann austauschen: so bleibt die Konfiguration auch bei einem Absturz heil.
            Path tmp = AppPaths.CONFIG.resolveSibling("launcher.json.tmp");
            Files.writeString(tmp, Util.GSON.toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, AppPaths.CONFIG, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, AppPaths.CONFIG, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println(I18n.t("Konfiguration konnte nicht gespeichert werden: ") + e);
        }
    }
}
