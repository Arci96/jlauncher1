package mclauncher;

import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
            System.err.println("Konfiguration konnte nicht geladen werden: " + e);
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(AppPaths.ROOT);
            Files.writeString(AppPaths.CONFIG, Util.GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Konfiguration konnte nicht gespeichert werden: " + e);
        }
    }
}
