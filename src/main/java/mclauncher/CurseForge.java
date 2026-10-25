package mclauncher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * CurseForge-Anbindung (https://docs.curseforge.com). Benötigt einen eigenen API-Schlüssel
 * von https://console.curseforge.com. Mods, deren Autor Downloads durch Drittprogramme
 * nicht erlaubt, werden nicht heruntergeladen - dafür gibt es den Link zur manuellen Installation.
 */
public final class CurseForge {
    private CurseForge() {}

    private static final String API = "https://api.curseforge.com/v1";
    private static final int GAME_MINECRAFT = 432;

    public static boolean hasKey() {
        String k = Store.data.config.curseForgeKey;
        return k != null && !k.isBlank();
    }

    private static int classId(ContentType t) {
        return switch (t) {
            case MOD -> 6;
            case RESOURCEPACK -> 12;
            case SHADER -> 6552;
        };
    }

    /** modLoaderType: 1 Forge, 4 Fabric, 5 Quilt, 6 NeoForge. Quilt lädt auch Fabric-Mods. */
    private static int loaderType(Loaders.Type t) {
        return switch (t) {
            case FORGE -> 1;
            case FABRIC, QUILT -> 4;
            case NEOFORGE -> 6;
            default -> 0;
        };
    }

    private static JsonObject get(String path) throws IOException {
        if (!hasKey()) throw new IOException("Kein CurseForge-API-Schlüssel eingetragen (Einstellungen → CurseForge).");
        try {
            String body = Util.get(API + path, Map.of("x-api-key", Store.data.config.curseForgeKey.trim(), "Accept", "application/json"));
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (IOException e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (m.contains("HTTP 401") || m.contains("HTTP 403")) {
                throw new IOException("Der CurseForge-API-Schlüssel wurde abgelehnt. Bitte prüfe ihn in den Einstellungen.");
            }
            throw e;
        }
    }

    public static List<Modrinth.Hit> search(String query, Instance inst, ContentType type, int offset) throws IOException {
        StringBuilder q = new StringBuilder("/mods/search?gameId=" + GAME_MINECRAFT
                + "&classId=" + classId(type)
                + "&gameVersion=" + Util.enc(inst.mcVersion)
                + "&sortField=2&sortOrder=desc&pageSize=20&index=" + offset);
        int lt = loaderType(inst.type());
        if (type == ContentType.MOD && lt > 0) q.append("&modLoaderType=").append(lt);
        if (query != null && !query.isBlank()) q.append("&searchFilter=").append(Util.enc(query));

        JsonObject r = get(q.toString());
        List<Modrinth.Hit> out = new ArrayList<>();
        for (JsonElement e : r.getAsJsonArray("data")) {
            JsonObject m = e.getAsJsonObject();
            String author = "";
            if (m.has("authors") && m.getAsJsonArray("authors").size() > 0) {
                author = Util.str(m.getAsJsonArray("authors").get(0).getAsJsonObject(), "name");
            }
            long downloads = m.has("downloadCount") ? m.get("downloadCount").getAsLong() : 0;
            out.add(new Modrinth.Hit(String.valueOf(m.get("id").getAsInt()), Util.str(m, "name"),
                    Util.str(m, "summary"), author == null ? "" : author, downloads));
        }
        return out;
    }

    public static void install(String modId, Instance inst, ContentType type, Set<String> visited,
                               Consumer<String> log) throws IOException {
        if (!visited.add(modId)) return;

        int lt = loaderType(inst.type());
        String path = "/mods/" + Util.enc(modId) + "/files?gameVersion=" + Util.enc(inst.mcVersion) + "&pageSize=30"
                + (type == ContentType.MOD && lt > 0 ? "&modLoaderType=" + lt : "");
        JsonArray arr = get(path).getAsJsonArray("data");
        if (arr.isEmpty()) throw new IOException("Keine passende Datei für " + inst.label() + " gefunden.");

        List<JsonObject> files = new ArrayList<>();
        for (JsonElement e : arr) files.add(e.getAsJsonObject());
        files.sort(Comparator.comparing((JsonObject f) -> Util.str(f, "fileDate") == null ? "" : Util.str(f, "fileDate")).reversed());
        JsonObject chosen = files.get(0);
        for (JsonObject f : files) {
            if (f.has("releaseType") && f.get("releaseType").getAsInt() == 1) {   // 1 = Release
                chosen = f;
                break;
            }
        }

        // Pflicht-Abhängigkeiten (relationType 3)
        if (type == ContentType.MOD && chosen.has("dependencies")) {
            for (JsonElement d : chosen.getAsJsonArray("dependencies")) {
                JsonObject dep = d.getAsJsonObject();
                if (!dep.has("relationType") || dep.get("relationType").getAsInt() != 3) continue;
                try {
                    install(String.valueOf(dep.get("modId").getAsInt()), inst, type, visited, log);
                } catch (IOException ex) {
                    log.accept("Abhängigkeit konnte nicht installiert werden: " + ex.getMessage());
                }
            }
        }

        String fileName = chosen.get("fileName").getAsString();
        if (!chosen.has("downloadUrl") || chosen.get("downloadUrl").isJsonNull()) {
            String site = "https://www.curseforge.com/minecraft";
            try {
                JsonObject info = get("/mods/" + Util.enc(modId)).getAsJsonObject("data");
                String u = info.has("links") ? Util.str(info.getAsJsonObject("links"), "websiteUrl") : null;
                if (u != null) site = u;
            } catch (IOException ignored) { }
            throw new IOException("Der Autor erlaubt keinen Download durch andere Programme. Lade \"" + fileName
                    + "\" manuell von " + site + " und lege die Datei in den Ordner "
                    + inst.contentDir(type).getFileName() + " des Profils.");
        }

        String sha1 = null;
        if (chosen.has("hashes")) {
            for (JsonElement h : chosen.getAsJsonArray("hashes")) {
                JsonObject ho = h.getAsJsonObject();
                if (ho.has("algo") && ho.get("algo").getAsInt() == 1) sha1 = Util.str(ho, "value");   // 1 = SHA-1
            }
        }
        Path dir = inst.contentDir(type);
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(dir.normalize())) throw new IOException("Ungültiger Dateiname: " + fileName);
        Files.createDirectories(dir);
        Util.download(chosen.get("downloadUrl").getAsString(), target, sha1, true);
        log.accept("Installiert: " + fileName);
    }
}
