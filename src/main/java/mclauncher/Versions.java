package mclauncher;

import com.google.gson.*;
import mclauncher.Util.Dl;
import mclauncher.Util.Progress;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Alles rund um Mojangs Versions-JSONs: Laden, Zusammenführen (inheritsFrom), Libraries, Natives, Assets. */
public final class Versions {
    private Versions() {}

    private static final String MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    public record Entry(String id, String type, String url, String sha1) {}

    public record LibFile(Path path, String url, String sha1) {}

    private static List<Entry> manifestCache;

    public static synchronized List<Entry> manifest() throws IOException {
        if (manifestCache == null) {
            JsonObject m = Util.getJson(MANIFEST);
            List<Entry> list = new ArrayList<>();
            for (JsonElement e : m.getAsJsonArray("versions")) {
                JsonObject v = e.getAsJsonObject();
                list.add(new Entry(Util.str(v, "id"), Util.str(v, "type"), Util.str(v, "url"), Util.str(v, "sha1")));
            }
            manifestCache = list;
        }
        return manifestCache;
    }

    public static Path versionJson(String id) {
        return AppPaths.VERSIONS.resolve(id).resolve(id + ".json");
    }

    /** Lädt das Versions-JSON einer Vanilla-Version (falls noch nicht vorhanden). */
    public static void installVanillaJson(String id) throws IOException {
        Path json = versionJson(id);
        if (Files.exists(json)) return;
        Entry e = manifest().stream().filter(x -> x.id().equals(id)).findFirst()
                .orElseThrow(() -> new IOException(I18n.t("Unbekannte Minecraft-Version: ") + id));
        Util.download(e.url(), json, e.sha1(), true);
    }

    /**
     * Liest ein Versions-JSON und führt es mit seinem Eltern-JSON (inheritsFrom) zusammen.
     * Fabric/Quilt/Forge/NeoForge sind alle "Kind"-Versionen von Vanilla.
     * Das Feld "_jar" enthält die ID der Version, deren Client-JAR verwendet wird.
     */
    public static JsonObject loadMerged(String id) throws IOException {
        JsonObject self = JsonParser.parseString(Files.readString(versionJson(id), StandardCharsets.UTF_8)).getAsJsonObject();
        if (!self.has("inheritsFrom")) {
            self.addProperty("_jar", id);
            return self;
        }
        JsonObject m = loadMerged(self.get("inheritsFrom").getAsString());
        for (Map.Entry<String, JsonElement> en : self.entrySet()) {
            switch (en.getKey()) {
                case "inheritsFrom", "_jar" -> { }
                case "libraries" -> {
                    JsonArray all = new JsonArray();
                    all.addAll(en.getValue().getAsJsonArray());           // Kind zuerst -> hat Vorrang
                    if (m.has("libraries")) all.addAll(m.getAsJsonArray("libraries"));
                    m.add("libraries", all);
                }
                case "arguments" -> {
                    JsonObject sa = en.getValue().getAsJsonObject();
                    JsonObject ma = m.has("arguments") ? m.getAsJsonObject("arguments") : new JsonObject();
                    for (String k : List.of("game", "jvm")) {
                        if (!sa.has(k)) continue;
                        JsonArray cat = new JsonArray();
                        if (ma.has(k)) cat.addAll(ma.getAsJsonArray(k));
                        cat.addAll(sa.getAsJsonArray(k));
                        ma.add(k, cat);
                    }
                    m.add("arguments", ma);
                }
                default -> m.add(en.getKey(), en.getValue());
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ Libraries

    private static String libKey(String name) {
        String[] p = name.split("@")[0].split(":");
        return p[0] + ":" + p[1] + (p.length > 3 ? ":" + p[3] : "");
    }

    /** Alle für dieses Betriebssystem aktiven Libraries, ohne Duplikate (die erste Fassung gewinnt). */
    public static List<JsonObject> libraries(JsonObject meta) {
        Map<String, JsonObject> map = new LinkedHashMap<>();
        if (!meta.has("libraries")) return new ArrayList<>();
        for (JsonElement e : meta.getAsJsonArray("libraries")) {
            JsonObject l = e.getAsJsonObject();
            if (l.has("rules") && !Util.rulesAllow(l.getAsJsonArray("rules"))) continue;
            map.putIfAbsent(libKey(l.get("name").getAsString()), l);
        }
        return new ArrayList<>(map.values());
    }

    public static LibFile mainArtifact(JsonObject l) {
        String name = l.get("name").getAsString();
        if (l.has("downloads")) {
            JsonObject dl = l.getAsJsonObject("downloads");
            if (!dl.has("artifact")) return null;
            JsonObject a = dl.getAsJsonObject("artifact");
            String path = a.has("path") ? a.get("path").getAsString() : Util.mavenPath(name);
            String url = a.has("url") ? a.get("url").getAsString() : "";
            return new LibFile(AppPaths.LIBRARIES.resolve(path), url, Util.str(a, "sha1"));
        }
        String path = Util.mavenPath(name);
        String base = l.has("url") ? l.get("url").getAsString() : "https://libraries.minecraft.net/";
        if (!base.endsWith("/")) base += "/";
        return new LibFile(AppPaths.LIBRARIES.resolve(path), base + path, Util.str(l, "sha1"));
    }

    /** Alte Natives (vor 1.19): Eintrag "natives" + "downloads.classifiers". */
    public static LibFile nativeFile(JsonObject l) {
        if (!l.has("natives") || !l.has("downloads")) return null;
        JsonObject natives = l.getAsJsonObject("natives");
        String osKey = Util.OS_TYPE.mojang;
        if (!natives.has(osKey)) return null;
        String classifier = natives.get(osKey).getAsString().replace("${arch}", Util.is64() ? "64" : "32");
        JsonObject classifiers = l.getAsJsonObject("downloads").getAsJsonObject("classifiers");
        if (classifiers == null || !classifiers.has(classifier)) return null;
        JsonObject a = classifiers.getAsJsonObject(classifier);
        return new LibFile(AppPaths.LIBRARIES.resolve(a.get("path").getAsString()), Util.str(a, "url"), Util.str(a, "sha1"));
    }

    public static Path clientJar(JsonObject meta) {
        String jarId = meta.get("_jar").getAsString();
        return AppPaths.VERSIONS.resolve(jarId).resolve(jarId + ".jar");
    }

    // ------------------------------------------------------------------ Installation

    public static void installFiles(String versionId, Progress p) throws IOException {
        JsonObject meta = loadMerged(versionId);
        List<Dl> tasks = new ArrayList<>();

        for (JsonObject l : libraries(meta)) {
            for (LibFile f : new LibFile[]{mainArtifact(l), nativeFile(l)}) {
                if (f == null) continue;
                if (f.url() == null || f.url().isEmpty()) {
                    // Wird z. B. vom Forge-Installer erzeugt - muss bereits da sein.
                    if (!Files.exists(f.path())) {
                        throw new IOException(I18n.t("Library fehlt und hat keine Download-URL: ") + f.path().getFileName());
                    }
                    continue;
                }
                tasks.add(new Dl(f.url(), f.path(), f.sha1(), true));
            }
        }

        JsonObject client = meta.getAsJsonObject("downloads").getAsJsonObject("client");
        tasks.add(new Dl(client.get("url").getAsString(), clientJar(meta), Util.str(client, "sha1"), true));

        // Log4j-Konfiguration (schützt ältere Versionen vor Log4Shell)
        JsonObject logging = loggingFile(meta);
        if (logging != null) {
            tasks.add(new Dl(logging.get("url").getAsString(), logConfigPath(logging), Util.str(logging, "sha1"), true));
        }

        Util.downloadAll(tasks, 8, p, "Libraries");
        installAssets(meta, p);
    }

    private static void installAssets(JsonObject meta, Progress p) throws IOException {
        if (!meta.has("assetIndex")) return;
        JsonObject ai = meta.getAsJsonObject("assetIndex");
        String id = ai.get("id").getAsString();
        Path idx = AppPaths.ASSETS.resolve("indexes").resolve(id + ".json");
        Util.download(ai.get("url").getAsString(), idx, Util.str(ai, "sha1"), true);

        JsonObject index = JsonParser.parseString(Files.readString(idx, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject objects = index.getAsJsonObject("objects");
        List<Dl> tasks = new ArrayList<>();
        for (Map.Entry<String, JsonElement> en : objects.entrySet()) {
            String h = en.getValue().getAsJsonObject().get("hash").getAsString();
            String pre = h.substring(0, 2);
            tasks.add(new Dl("https://resources.download.minecraft.net/" + pre + "/" + h,
                    AppPaths.ASSETS.resolve("objects").resolve(pre).resolve(h), h, false));
        }
        Util.downloadAll(tasks, 16, p, "Assets");

        // Sehr alte Versionen erwarten eine "virtuelle" Ordnerstruktur
        boolean virtual = index.has("virtual") && index.get("virtual").getAsBoolean();
        if (virtual) {
            Path vdir = AppPaths.ASSETS.resolve("virtual").resolve(id);
            for (Map.Entry<String, JsonElement> en : objects.entrySet()) {
                String h = en.getValue().getAsJsonObject().get("hash").getAsString();
                Path target = vdir.resolve(en.getKey());
                if (Files.exists(target)) continue;
                Files.createDirectories(target.getParent());
                Files.copy(AppPaths.ASSETS.resolve("objects").resolve(h.substring(0, 2)).resolve(h), target);
            }
        }
    }

    public static JsonObject loggingFile(JsonObject meta) {
        if (!meta.has("logging")) return null;
        JsonObject client = meta.getAsJsonObject("logging").getAsJsonObject("client");
        if (client == null || !client.has("file")) return null;
        return client.getAsJsonObject("file");
    }

    public static Path logConfigPath(JsonObject loggingFile) {
        return AppPaths.ASSETS.resolve("log_configs").resolve(loggingFile.get("id").getAsString());
    }

    /** Entpackt die alten Native-Libraries (< 1.19) und gibt das Natives-Verzeichnis zurück. */
    public static Path prepareNatives(JsonObject meta, String versionId) throws IOException {
        Path dir = AppPaths.VERSIONS.resolve(versionId).resolve("natives");
        Files.createDirectories(dir);
        for (JsonObject l : libraries(meta)) {
            LibFile nf = nativeFile(l);
            if (nf == null || !Files.exists(nf.path())) continue;
            List<String> exclude = new ArrayList<>();
            if (l.has("extract") && l.getAsJsonObject("extract").has("exclude")) {
                for (JsonElement x : l.getAsJsonObject("extract").getAsJsonArray("exclude")) exclude.add(x.getAsString());
            }
            try (InputStream in = Files.newInputStream(nf.path()); ZipInputStream zin = new ZipInputStream(in)) {
                ZipEntry ze;
                outer:
                while ((ze = zin.getNextEntry()) != null) {
                    String n = ze.getName();
                    if (ze.isDirectory() || n.startsWith("META-INF")) continue;
                    for (String ex : exclude) if (n.startsWith(ex)) continue outer;
                    Path target = dir.resolve(n).normalize();
                    if (!target.startsWith(dir)) continue;   // Zip-Slip-Schutz
                    if (Files.exists(target)) continue;
                    Files.createDirectories(target.getParent());
                    Files.copy(zin, target);
                }
            }
        }
        return dir;
    }
}
