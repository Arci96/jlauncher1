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

/** Installiert alles Nötige und startet Minecraft als Kindprozess. */
public final class Launcher {
    private Launcher() {}

    public static Process start(Instance inst, Auth.Account acc, String server, Progress p, Consumer<String> log) throws IOException {
        Path gameDir = inst.dir();
        Files.createDirectories(gameDir);

        // 1. Vanilla
        p.update("Minecraft " + inst.mcVersion + " wird geprüft …", -1);
        Versions.installVanillaJson(inst.mcVersion);
        JsonObject vanilla = Versions.loadMerged(inst.mcVersion);
        int javaMajor = vanilla.has("javaVersion") ? vanilla.getAsJsonObject("javaVersion").get("majorVersion").getAsInt() : 8;
        String java = resolveJava(inst, javaMajor);
        log.accept("Java: " + java);
        Versions.installFiles(inst.mcVersion, p);

        // 2. Loader
        String versionId = inst.mcVersion;
        if (inst.type() != Loaders.Type.VANILLA) {
            if (inst.versionId == null || !Files.exists(Versions.versionJson(inst.versionId))) {
                inst.versionId = Loaders.install(inst, java, p, log);
                Store.save();
            }
            versionId = inst.versionId;
            Versions.installFiles(versionId, p);
        }

        // 3. Befehl bauen
        p.update("Starte Minecraft …", -1);
        JsonObject meta = Versions.loadMerged(versionId);
        Path natives = Versions.prepareNatives(meta, versionId);

        StringJoiner cp = new StringJoiner(Util.cpSeparator());
        for (JsonObject l : Versions.libraries(meta)) {
            Versions.LibFile f = Versions.mainArtifact(l);
            if (f != null && Files.exists(f.path())) cp.add(f.path().toString());
        }
        cp.add(Versions.clientJar(meta).toString());

        String assetsId = meta.has("assetIndex") ? meta.getAsJsonObject("assetIndex").get("id").getAsString()
                : Util.str(meta, "assets");
        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", acc.name());
        vars.put("auth_uuid", acc.uuid());
        vars.put("auth_access_token", acc.token());
        vars.put("auth_session", "token:" + acc.token() + ":" + acc.uuid());
        vars.put("user_type", acc.userType());
        vars.put("user_properties", "{}");
        vars.put("clientid", "");
        vars.put("auth_xuid", "");
        vars.put("version_name", versionId);
        vars.put("version_type", meta.has("type") ? meta.get("type").getAsString() : "release");
        vars.put("game_directory", gameDir.toString());
        vars.put("assets_root", AppPaths.ASSETS.toString());
        vars.put("game_assets", AppPaths.ASSETS.resolve("virtual").resolve(String.valueOf(assetsId)).toString());
        vars.put("assets_index_name", String.valueOf(assetsId));
        vars.put("natives_directory", natives.toString());
        vars.put("launcher_name", "JLauncher");
        vars.put("launcher_version", "1.0");
        vars.put("classpath", cp.toString());
        vars.put("library_directory", AppPaths.LIBRARIES.toString());
        vars.put("classpath_separator", Util.cpSeparator());
        vars.put("quickPlayMultiplayer", server == null ? "" : server);
        Set<String> features = server == null ? Set.of() : Set.of("is_quick_play_multiplayer");

        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        cmd.add("-Xms" + inst.ramMin + "M");
        cmd.add("-Xmx" + inst.ramMax + "M");
        cmd.addAll(Util.splitArgs(inst.jvmArgs));

        if (meta.has("arguments") && meta.getAsJsonObject("arguments").has("jvm")) {
            cmd.addAll(collect(meta.getAsJsonObject("arguments").getAsJsonArray("jvm"), vars, features));
        } else {
            cmd.add("-Djava.library.path=" + natives);
            cmd.add("-cp");
            cmd.add(cp.toString());
        }

        JsonObject logging = Versions.loggingFile(meta);
        if (logging != null) {
            String arg = meta.getAsJsonObject("logging").getAsJsonObject("client").get("argument").getAsString();
            cmd.add(arg.replace("${path}", Versions.logConfigPath(logging).toString()));
        }

        cmd.add(meta.get("mainClass").getAsString());

        if (meta.has("arguments") && meta.getAsJsonObject("arguments").has("game")) {
            cmd.addAll(collect(meta.getAsJsonObject("arguments").getAsJsonArray("game"), vars, features));
        } else if (meta.has("minecraftArguments")) {
            for (String a : meta.get("minecraftArguments").getAsString().split(" ")) cmd.add(substitute(a, vars));
        }

        // Ältere Versionen (< 1.20) kennen --quickPlayMultiplayer nicht -> klassische Server-Argumente
        if (server != null && !cmd.contains("--quickPlayMultiplayer")) {
            String host = server, port = "25565";
            int i = server.lastIndexOf(':');
            if (i > 0 && server.indexOf(':') == i) {
                host = server.substring(0, i);
                port = server.substring(i + 1);
            }
            cmd.add("--server");
            cmd.add(host);
            cmd.add("--port");
            cmd.add(port);
        }

        // Kommando im Log zeigen (Token maskiert)
        String shown = String.join(" ", cmd);
        if (acc.token().length() > 3) shown = shown.replace(acc.token(), "***");
        log.accept("$ " + shown);

        Process proc = new ProcessBuilder(cmd).directory(gameDir.toFile()).redirectErrorStream(true).start();
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) log.accept(line);
            } catch (IOException ignored) { }
        }, "game-output");
        t.setDaemon(true);
        t.start();
        return proc;
    }

    private static String resolveJava(Instance inst, int major) throws IOException {
        if (inst.javaPath != null && !inst.javaPath.isBlank()) return inst.javaPath;
        String def = Store.data.config.defaultJavaPath;
        if (def != null && !def.isBlank()) return def;
        String found = JavaFinder.find(major);
        if (found == null) {
            throw new IOException("Java " + major + " wurde nicht gefunden. Bitte installiere es (z. B. Eclipse Temurin "
                    + major + ") oder trage in den Instanz-Einstellungen einen Java-Pfad ein.");
        }
        return found;
    }

    /** Wertet ein "arguments"-Array (Strings und Regel-Objekte) aus und ersetzt ${Variablen}. */
    private static List<String> collect(JsonArray args, Map<String, String> vars, Set<String> features) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : args) {
            if (e.isJsonPrimitive()) {
                out.add(substitute(e.getAsString(), vars));
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            if (o.has("rules") && !Util.rulesAllow(o.getAsJsonArray("rules"), features)) continue;
            JsonElement v = o.get("value");
            if (v.isJsonArray()) {
                for (JsonElement x : v.getAsJsonArray()) out.add(substitute(x.getAsString(), vars));
            } else {
                out.add(substitute(v.getAsString(), vars));
            }
        }
        return out;
    }

    private static String substitute(String s, Map<String, String> vars) {
        for (Map.Entry<String, String> en : vars.entrySet()) {
            s = s.replace("${" + en.getKey() + "}", en.getValue());
        }
        return s;
    }
}
