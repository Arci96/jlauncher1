package mclauncher;

import com.google.gson.*;
import mclauncher.Util.Progress;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Versionslisten und Installation der Mod-Loader. */
public final class Loaders {
    private Loaders() {}

    public enum Type {
        VANILLA("Vanilla"), FABRIC("Fabric"), QUILT("Quilt"), FORGE("Forge"), NEOFORGE("NeoForge");
        public final String label;
        Type(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    private static final String QUILT_META = "https://meta.quiltmc.org/v3";

    // ------------------------------------------------------------------ Versionslisten

    /** Verfügbare Loader-Versionen für eine Minecraft-Version, neueste zuerst. */
    public static List<String> versions(Type t, String mc) throws IOException {
        switch (t) {
            case FABRIC: return metaVersions(FABRIC_META + "/versions/loader/" + Util.enc(mc));
            case QUILT: return metaVersions(QUILT_META + "/versions/loader/" + Util.enc(mc));
            case FORGE: return forgeVersions(mc);
            case NEOFORGE: return neoForgeVersions(mc);
            default: return List.of();
        }
    }

    private static List<String> metaVersions(String url) throws IOException {
        JsonArray arr = Util.getJsonAny(url).getAsJsonArray();
        List<String> all = new ArrayList<>(), stable = new ArrayList<>();
        for (JsonElement e : arr) {
            String v = e.getAsJsonObject().getAsJsonObject("loader").get("version").getAsString();
            all.add(v);
            if (!v.contains("-")) stable.add(v);   // "0.15.0-beta.3" o. Ä. ausfiltern
        }
        return stable.isEmpty() ? all : stable;
    }

    private static List<String> forgeVersions(String mc) throws IOException {
        String xml = Util.get("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml");
        Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(xml);
        List<String> out = new ArrayList<>();
        String prefix = mc + "-";
        while (m.find()) {
            String v = m.group(1);
            if (v.startsWith(prefix)) out.add(v.substring(prefix.length()));
        }
        Collections.reverse(out);
        return out;
    }

    private static List<String> neoForgeVersions(String mc) throws IOException {
        // NeoForge-Schema: MC 1.21.1 -> 21.1.x, MC 1.21 -> 21.0.x. (1.20.1 nutzt noch das alte Forge-Schema.)
        String[] parts = mc.split("\\.");
        if (parts.length < 2 || mc.equals("1.20.1")) return List.of();
        String prefix = parts[1] + "." + (parts.length > 2 ? parts[2] : "0") + ".";
        JsonObject o = Util.getJson("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge");
        List<String> out = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("versions")) {
            String v = e.getAsString();
            if (v.startsWith(prefix)) out.add(v);
        }
        Collections.reverse(out);
        return out;
    }

    // ------------------------------------------------------------------ Installation

    /**
     * Installiert den Loader und gibt die Versions-ID zurück, die später gestartet wird.
     * Fabric/Quilt: Profil-JSON laden. Forge/NeoForge: Offiziellen Installer headless ausführen.
     */
    public static String install(Instance inst, String javaExe, Progress p, Consumer<String> log) throws IOException {
        Type t = inst.type();
        String mc = inst.mcVersion, lv = inst.loaderVersion;
        if (lv == null || lv.isBlank()) throw new IOException(I18n.t("Keine Loader-Version gewählt."));
        p.update(t.label + " " + lv + I18n.t(" wird installiert …"), -1);

        if (t == Type.FABRIC || t == Type.QUILT) {
            String base = t == Type.FABRIC ? FABRIC_META : QUILT_META;
            String json = Util.get(base + "/versions/loader/" + Util.enc(mc) + "/" + Util.enc(lv) + "/profile/json");
            String id = JsonParser.parseString(json).getAsJsonObject().get("id").getAsString();
            Path f = Versions.versionJson(id);
            Files.createDirectories(f.getParent());
            Files.writeString(f, json, StandardCharsets.UTF_8);
            return id;
        }
        if (t == Type.FORGE || t == Type.NEOFORGE) {
            return runInstaller(t, mc, lv, javaExe, log);
        }
        return mc;
    }

    private static String runInstaller(Type t, String mc, String lv, String javaExe, Consumer<String> log) throws IOException {
        String url, expected;
        if (t == Type.FORGE) {
            url = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + mc + "-" + lv + "/forge-" + mc + "-" + lv + "-installer.jar";
            expected = mc + "-forge-" + lv;
        } else {
            url = "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + lv + "/neoforge-" + lv + "-installer.jar";
            expected = "neoforge-" + lv;
        }
        Path jar = AppPaths.CACHE.resolve("installers").resolve(t.name().toLowerCase() + "-" + mc + "-" + lv + "-installer.jar");
        log.accept(I18n.t("Lade Installer: ") + url);
        Util.download(url, jar, null, false);

        // Der Installer erwartet ein launcher_profiles.json im Zielordner.
        Path profiles = AppPaths.ROOT.resolve("launcher_profiles.json");
        if (!Files.exists(profiles)) Files.writeString(profiles, "{\"profiles\":{},\"version\":3}");

        Set<String> before = versionDirs();
        List<String> cmd = List.of(javaExe, "-Djava.awt.headless=true", "-jar", jar.toString(),
                "--installClient", AppPaths.ROOT.toString());
        log.accept(I18n.t("Starte Installer (das kann einige Minuten dauern) …"));
        Process proc = new ProcessBuilder(cmd).directory(AppPaths.ROOT.toFile()).redirectErrorStream(true).start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) log.accept("[Installer] " + line);
        }
        int code;
        try {
            code = proc.waitFor();
        } catch (InterruptedException e) {
            proc.destroy();
            Thread.currentThread().interrupt();
            throw new IOException(I18n.t("Abgebrochen"), e);
        }
        if (code != 0) throw new IOException(I18n.t("Der ") + t.label + I18n.t("-Installer ist mit Code ") + code + I18n.t(" fehlgeschlagen (siehe Konsole)."));

        if (Files.exists(Versions.versionJson(expected))) return expected;
        Set<String> created = versionDirs();
        created.removeAll(before);
        if (created.size() == 1) return created.iterator().next();
        throw new IOException(I18n.t("Konnte die installierte Version nicht finden (erwartet: ") + expected + ").");
    }

    private static Set<String> versionDirs() throws IOException {
        Set<String> out = new HashSet<>();
        if (!Files.isDirectory(AppPaths.VERSIONS)) return out;
        try (Stream<Path> s = Files.list(AppPaths.VERSIONS)) {
            s.filter(Files::isDirectory).forEach(p -> out.add(p.getFileName().toString()));
        }
        return out;
    }
}
