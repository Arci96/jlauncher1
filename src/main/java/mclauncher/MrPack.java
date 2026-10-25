package mclauncher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Import von Modrinth-Modpacks (.mrpack): legt ein neues Profil an, lädt alle Mods und entpackt die Overrides. */
public final class MrPack {
    private MrPack() {}

    /** Laut Modrinth-Spezifikation sind nur diese Download-Quellen erlaubt. */
    private static final Set<String> HOSTS = Set.of("cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com");

    public static Instance importPack(Path pack, Util.Progress p, Consumer<String> log) throws IOException {
        Instance inst = new Instance();
        Path gameDir = null;
        try (ZipFile zf = new ZipFile(pack.toFile())) {
            ZipEntry idx = zf.getEntry("modrinth.index.json");
            if (idx == null) throw new IOException("Das ist kein gültiges Modrinth-Modpack (modrinth.index.json fehlt).");
            JsonObject index;
            try (InputStream in = zf.getInputStream(idx)) {
                index = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }

            JsonObject deps = index.has("dependencies") ? index.getAsJsonObject("dependencies") : null;
            String mc = Util.str(deps, "minecraft");
            if (mc == null) throw new IOException("Im Modpack ist keine Minecraft-Version angegeben.");

            Loaders.Type type = Loaders.Type.VANILLA;
            String lv = "";
            if (Util.str(deps, "fabric-loader") != null) { type = Loaders.Type.FABRIC; lv = Util.str(deps, "fabric-loader"); }
            else if (Util.str(deps, "quilt-loader") != null) { type = Loaders.Type.QUILT; lv = Util.str(deps, "quilt-loader"); }
            else if (Util.str(deps, "neoforge") != null) { type = Loaders.Type.NEOFORGE; lv = Util.str(deps, "neoforge"); }
            else if (Util.str(deps, "forge") != null) { type = Loaders.Type.FORGE; lv = Util.str(deps, "forge"); }

            String name = Util.str(index, "name");
            inst.name = name == null || name.isBlank() ? pack.getFileName().toString().replace(".mrpack", "") : name;
            inst.mcVersion = mc;
            inst.loader = type.name();
            inst.loaderVersion = lv;
            inst.ramMin = Store.data.config.defaultRamMin;
            inst.ramMax = Math.max(Store.data.config.defaultRamMax, 4096);   // Modpacks brauchen meist mehr RAM
            inst.fpsLimit = Store.data.config.defaultFpsLimit;
            gameDir = inst.dir();
            Files.createDirectories(gameDir);
            Path base = gameDir.normalize();

            List<Util.Dl> tasks = new ArrayList<>();
            if (index.has("files")) {
                for (JsonElement fe : index.getAsJsonArray("files")) {
                    JsonObject f = fe.getAsJsonObject();
                    if (f.has("env") && "unsupported".equals(Util.str(f.getAsJsonObject("env"), "client"))) continue;
                    String rel = f.get("path").getAsString();
                    Path target = base.resolve(rel).normalize();
                    if (!target.startsWith(base)) throw new IOException("Unsicherer Pfad im Modpack: " + rel);
                    String url = f.getAsJsonArray("downloads").get(0).getAsString();
                    String host = URI.create(url).getHost();
                    if (host == null || !HOSTS.contains(host.toLowerCase())) {
                        throw new IOException("Download-Quelle im Modpack nicht erlaubt: " + host);
                    }
                    String sha1 = f.has("hashes") ? Util.str(f.getAsJsonObject("hashes"), "sha1") : null;
                    tasks.add(new Util.Dl(url, target, sha1, true));
                }
            }
            log.accept("Modpack \"" + inst.name + "\": " + tasks.size() + " Dateien, Minecraft " + mc
                    + (type == Loaders.Type.VANILLA ? "" : ", " + type.label + " " + lv));
            p.update("Lade Modpack-Dateien …", 0);
            Util.downloadAll(tasks, 8, p, "Modpack-Dateien");

            p.update("Entpacke Konfigurationen …", -1);
            for (String prefix : new String[]{"overrides/", "client-overrides/"}) {
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName();
                    if (e.isDirectory() || !n.startsWith(prefix)) continue;
                    Path target = base.resolve(n.substring(prefix.length())).normalize();
                    if (!target.startsWith(base)) continue;   // Zip-Slip-Schutz
                    Files.createDirectories(target.getParent());
                    try (InputStream in = zf.getInputStream(e)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return inst;
        } catch (IOException | RuntimeException e) {
            if (gameDir != null) {
                try { Util.deleteRecursive(gameDir); } catch (IOException ignored) { }
            }
            throw e;
        }
    }
}
