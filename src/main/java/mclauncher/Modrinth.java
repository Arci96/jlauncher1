package mclauncher;

import com.google.gson.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Modrinth-Anbindung (https://docs.modrinth.com/api) für Mods, Ressourcenpakete und Shader. */
public final class Modrinth {
    private Modrinth() {}

    private static final String API = "https://api.modrinth.com/v2";

    public record Hit(String id, String title, String description, String author, long downloads) {}

    private static List<String> loaderNames(Loaders.Type t) {
        return switch (t) {
            case FABRIC -> List.of("fabric");
            case QUILT -> List.of("quilt", "fabric");   // Quilt lädt auch Fabric-Mods
            case FORGE -> List.of("forge");
            case NEOFORGE -> List.of("neoforge");
            default -> List.of();
        };
    }

    private static JsonArray strings(Collection<String> values) {
        JsonArray a = new JsonArray();
        for (String v : values) a.add(v);
        return a;
    }

    public static List<Hit> search(String query, Instance inst, ContentType type, int offset) throws IOException {
        JsonArray facets = new JsonArray();
        facets.add(strings(List.of("project_type:" + type.modrinth)));
        facets.add(strings(List.of("versions:" + inst.mcVersion)));
        if (type == ContentType.MOD) {
            List<String> cats = new ArrayList<>();
            for (String l : loaderNames(inst.type())) cats.add("categories:" + l);
            if (!cats.isEmpty()) facets.add(strings(cats));   // innerhalb einer Gruppe = ODER
        }

        String url = API + "/search?query=" + Util.enc(query == null ? "" : query)
                + "&limit=20&offset=" + offset
                + "&index=" + (query == null || query.isBlank() ? "downloads" : "relevance")
                + "&facets=" + Util.enc(facets.toString());
        JsonObject r = Util.getJson(url);
        List<Hit> out = new ArrayList<>();
        for (JsonElement e : r.getAsJsonArray("hits")) {
            JsonObject h = e.getAsJsonObject();
            out.add(new Hit(Util.str(h, "project_id"), Util.str(h, "title"), Util.str(h, "description"),
                    Util.str(h, "author"), h.has("downloads") ? h.get("downloads").getAsLong() : 0));
        }
        return out;
    }

    /** Installiert die neueste passende Version (bei Mods samt Pflicht-Abhängigkeiten). */
    public static void install(String projectId, Instance inst, ContentType type, Set<String> visited,
                               Consumer<String> log) throws IOException {
        if (!visited.add(projectId)) return;

        String url = API + "/project/" + Util.enc(projectId) + "/version"
                + "?game_versions=" + Util.enc(strings(List.of(inst.mcVersion)).toString());
        if (type == ContentType.MOD) {
            url += "&loaders=" + Util.enc(strings(loaderNames(inst.type())).toString());
        }
        JsonArray versions = Util.getJsonAny(url).getAsJsonArray();
        if (versions.isEmpty()) throw new IOException("Keine passende Version für " + inst.label() + " gefunden.");

        JsonObject chosen = versions.get(0).getAsJsonObject();
        for (JsonElement e : versions) {
            if ("release".equals(Util.str(e.getAsJsonObject(), "version_type"))) {
                chosen = e.getAsJsonObject();
                break;
            }
        }

        if (type == ContentType.MOD && chosen.has("dependencies")) {
            for (JsonElement d : chosen.getAsJsonArray("dependencies")) {
                JsonObject dep = d.getAsJsonObject();
                if (!"required".equals(Util.str(dep, "dependency_type"))) continue;
                String depId = Util.str(dep, "project_id");
                if (depId == null) continue;
                try {
                    install(depId, inst, type, visited, log);
                } catch (IOException ex) {
                    log.accept("Abhängigkeit " + depId + " konnte nicht installiert werden: " + ex.getMessage());
                }
            }
        }

        JsonArray files = chosen.getAsJsonArray("files");
        JsonObject file = files.get(0).getAsJsonObject();
        for (JsonElement f : files) {
            if (f.getAsJsonObject().has("primary") && f.getAsJsonObject().get("primary").getAsBoolean()) {
                file = f.getAsJsonObject();
                break;
            }
        }
        String filename = file.get("filename").getAsString();
        String sha1 = file.has("hashes") ? Util.str(file.getAsJsonObject("hashes"), "sha1") : null;
        Path dir = inst.contentDir(type);
        Path target = dir.resolve(filename).normalize();
        if (!target.startsWith(dir.normalize())) throw new IOException("Ungültiger Dateiname: " + filename);

        Files.createDirectories(dir);
        Util.download(file.get("url").getAsString(), target, sha1, true);
        log.accept("Installiert: " + filename);
    }

    // ------------------------------------------------------------------ Installierte Inhalte

    public static List<Path> installed(Instance inst, ContentType type) throws IOException {
        Path dir = inst.contentDir(type);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                if (type == ContentType.MOD) return n.endsWith(".jar") || n.endsWith(".jar.disabled");
                return Files.isDirectory(p) || n.endsWith(".zip") || n.endsWith(".zip.disabled");
            }).sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase())).toList();
        }
    }

    public static boolean isDisabled(Path p) {
        return p.getFileName().toString().endsWith(".disabled");
    }

    public static void toggle(Path p) throws IOException {
        String n = p.getFileName().toString();
        String target = isDisabled(p) ? n.substring(0, n.length() - ".disabled".length()) : n + ".disabled";
        Files.move(p, p.resolveSibling(target), StandardCopyOption.REPLACE_EXISTING);
    }

    public static void delete(Path p) throws IOException {
        Util.deleteRecursive(p);
    }
}
