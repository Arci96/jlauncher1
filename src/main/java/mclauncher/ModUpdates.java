package mclauncher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Sucht bei Modrinth neuere Versionen der installierten Mods (Abgleich über SHA-1 der Dateien). */
public final class ModUpdates {
    private ModUpdates() {}

    public record Update(Path file, String oldName, String newFileName, String newVersion,
                         String url, String sha1, boolean disabled) {}

    private static final String API = "https://api.modrinth.com/v2";

    public static List<Update> check(Instance inst) throws IOException {
        List<Update> out = new ArrayList<>();
        if (inst.type() == Loaders.Type.VANILLA) return out;

        Map<String, Path> byHash = new HashMap<>();
        for (Path p : Modrinth.installed(inst, ContentType.MOD)) byHash.put(Util.sha1(p), p);
        if (byHash.isEmpty()) return out;

        JsonArray hashes = new JsonArray();
        byHash.keySet().forEach(hashes::add);
        JsonArray loaders = new JsonArray();
        for (String l : loaderNames(inst.type())) loaders.add(l);
        JsonArray versions = new JsonArray();
        versions.add(inst.mcVersion);

        JsonObject req = new JsonObject();
        req.add("hashes", hashes);
        req.addProperty("algorithm", "sha1");
        req.add("loaders", loaders);
        req.add("game_versions", versions);

        Util.Resp r = Util.postJson(API + "/version_files/update", req);
        if (!r.ok()) throw new IOException("Modrinth antwortet mit HTTP " + r.status());

        for (Map.Entry<String, JsonElement> e : r.json().entrySet()) {
            Path file = byHash.get(e.getKey());
            if (file == null) continue;
            JsonObject v = e.getValue().getAsJsonObject();
            JsonArray files = v.getAsJsonArray("files");
            if (files == null || files.isEmpty()) continue;
            JsonObject f = files.get(0).getAsJsonObject();
            for (JsonElement fe : files) {
                if (fe.getAsJsonObject().has("primary") && fe.getAsJsonObject().get("primary").getAsBoolean()) f = fe.getAsJsonObject();
            }
            String newSha = f.has("hashes") ? Util.str(f.getAsJsonObject("hashes"), "sha1") : null;
            if (newSha != null && newSha.equalsIgnoreCase(e.getKey())) continue;   // bereits aktuell
            out.add(new Update(file, file.getFileName().toString(), f.get("filename").getAsString(),
                    Util.str(v, "version_number"), f.get("url").getAsString(), newSha, Modrinth.isDisabled(file)));
        }
        return out;
    }

    public static void apply(Instance inst, List<Update> updates, Consumer<String> log) throws IOException {
        for (Update u : updates) {
            Path dir = u.file().getParent();
            Path target = dir.resolve(u.newFileName() + (u.disabled() ? ".disabled" : "")).normalize();
            if (!target.startsWith(dir.normalize())) throw new IOException("Ungültiger Dateiname: " + u.newFileName());
            log.accept("Aktualisiere " + u.oldName() + " → " + u.newFileName());
            Util.download(u.url(), target, u.sha1(), true);
            if (!target.equals(u.file())) Files.deleteIfExists(u.file());
        }
    }

    private static List<String> loaderNames(Loaders.Type t) {
        return switch (t) {
            case FABRIC -> List.of("fabric");
            case QUILT -> List.of("quilt", "fabric");
            case FORGE -> List.of("forge");
            case NEOFORGE -> List.of("neoforge");
            default -> List.of();
        };
    }
}
