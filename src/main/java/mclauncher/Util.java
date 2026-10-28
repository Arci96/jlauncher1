package mclauncher;

import com.google.gson.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class Util {
    private Util() {}

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String UA = "JLauncher/1.0";
    /** Zähler aller heruntergeladenen Bytes (für Geschwindigkeitsanzeige). */
    public static final AtomicLong BYTES = new AtomicLong();
    public static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /** Fortschrittsmeldung. fraction < 0 bedeutet "unbestimmt". */
    @FunctionalInterface
    public interface Progress {
        void update(String message, double fraction);
    }

    // ------------------------------------------------------------------ Betriebssystem

    public enum OS {
        WINDOWS("windows"), LINUX("linux"), MACOS("osx");
        public final String mojang;
        OS(String mojang) { this.mojang = mojang; }
    }

    public static final OS OS_TYPE = detectOs();

    private static OS detectOs() {
        String n = System.getProperty("os.name").toLowerCase();
        if (n.contains("win")) return OS.WINDOWS;
        if (n.contains("mac")) return OS.MACOS;
        return OS.LINUX;
    }

    public static boolean is64() { return System.getProperty("os.arch").contains("64"); }

    public static boolean isArm() {
        String a = System.getProperty("os.arch").toLowerCase();
        return a.contains("aarch64") || a.contains("arm64");
    }

    // ------------------------------------------------------------------ Async

    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "jlauncher-worker");
        t.setDaemon(true);
        return t;
    });

    public static void async(Runnable r) { POOL.execute(r); }

    // ------------------------------------------------------------------ HTTP

    private static HttpRequest.Builder req(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", UA);
    }

    public record Resp(int status, String body) {
        public boolean ok() { return status / 100 == 2; }
        public JsonObject json() { return JsonParser.parseString(body).getAsJsonObject(); }
    }

    public static Resp post(String url, String contentType, String body, Map<String, String> headers) throws IOException {
        HttpRequest.Builder b = req(url).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (headers != null) headers.forEach(b::header);
        return exec(b.build());
    }

    public static Resp postJson(String url, JsonObject body) throws IOException {
        return post(url, "application/json", body.toString(), Map.of("Accept", "application/json"));
    }

    public static Resp postForm(String url, Map<String, String> form) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (var e : form.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return post(url, "application/x-www-form-urlencoded", sb.toString(), null);
    }

    private static Resp exec(HttpRequest r) throws IOException {
        try {
            HttpResponse<String> resp = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
            return new Resp(resp.statusCode(), resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(I18n.t("Abgebrochen"), e);
        }
    }

    public static String get(String url) throws IOException {
        return get(url, null);
    }

    public static String get(String url, Map<String, String> headers) throws IOException {
        HttpRequest.Builder b = req(url).GET();
        if (headers != null) headers.forEach(b::header);
        Resp r = exec(b.build());
        if (!r.ok()) {
            String body = r.body() == null ? "" : r.body();
            throw new IOException("HTTP " + r.status() + I18n.t(" bei ") + url + (body.length() > 200 ? "" : ": " + body));
        }
        return r.body();
    }

    public static JsonObject getJson(String url) throws IOException {
        return JsonParser.parseString(get(url)).getAsJsonObject();
    }

    public static JsonElement getJsonAny(String url) throws IOException {
        return JsonParser.parseString(get(url));
    }

    public static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ Downloads

    public record Dl(String url, Path dest, String sha1, boolean verifyExisting) {}

    /** Lädt eine Datei herunter und versucht es bei Netzwerkfehlern bis zu dreimal. */
    public static void download(String url, Path dest, String sha1, boolean verifyExisting) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                downloadOnce(url, dest, sha1, verifyExisting);
                return;
            } catch (IOException e) {
                last = e;
                String m = e.getMessage() == null ? "" : e.getMessage();
                if (Thread.currentThread().isInterrupted() || m.equals(I18n.t("Abgebrochen")) || m.contains("HTTP 404")) throw e;
                if (attempt < 3) {
                    try {
                        Thread.sleep(800L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException(I18n.t("Abgebrochen"), ie);
                    }
                }
            }
        }
        throw last;
    }

    /** Gesamter Arbeitsspeicher des Rechners in MB. */
    public static int totalMemoryMb() {
        try {
            var os = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return (int) Math.max(2048, Math.min(os.getTotalMemorySize() / (1024 * 1024), 1_000_000));
        } catch (Throwable t) {
            return 8192;
        }
    }

    private static void downloadOnce(String url, Path dest, String sha1, boolean verifyExisting) throws IOException {
        if (Files.exists(dest)) {
            if (!verifyExisting || sha1 == null || sha1.equalsIgnoreCase(sha1(dest))) return;
        }
        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName() + "." + Thread.currentThread().getId() + ".part");
        try {
            HttpResponse<InputStream> r = HTTP.send(req(url).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = r.body(); OutputStream os = Files.newOutputStream(tmp)) {
                if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + I18n.t(" bei ") + url);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    BYTES.addAndGet(n);
                }
            }
            if (sha1 != null && !sha1.equalsIgnoreCase(sha1(tmp))) {
                throw new IOException(I18n.t("Prüfsumme stimmt nicht: ") + url);
            }
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(I18n.t("Abgebrochen"), e);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static void downloadAll(List<Dl> tasks, int threads, Progress p, String label) throws IOException {
        if (tasks.isEmpty()) return;
        ExecutorService ex = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "jlauncher-dl");
            t.setDaemon(true);
            return t;
        });
        AtomicInteger done = new AtomicInteger();
        int total = tasks.size();
        List<Future<Void>> futures = new ArrayList<>();
        for (Dl t : tasks) {
            futures.add(ex.submit((Callable<Void>) () -> {
                download(t.url(), t.dest(), t.sha1(), t.verifyExisting());
                int d = done.incrementAndGet();
                if (p != null && (d % 8 == 0 || d == total)) {
                    p.update(label + " (" + d + "/" + total + ")", (double) d / total);
                }
                return null;
            }));
        }
        try {
            for (Future<Void> f : futures) f.get();
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof CancellationException) throw new IOException(I18n.t("Abgebrochen"));
            throw c instanceof IOException io ? io : new IOException(c);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(I18n.t("Abgebrochen"), e);
        } finally {
            ex.shutdownNow();
        }
    }

    public static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    // ------------------------------------------------------------------ Mojang-Regeln

    public static boolean rulesAllow(JsonArray rules) {
        return rulesAllow(rules, Set.of());
    }

    /** Wertet Mojang-Regeln aus; {@code features} sind die aktiven Feature-Flags (z. B. is_quick_play_multiplayer). */
    public static boolean rulesAllow(JsonArray rules, Set<String> features) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (JsonElement e : rules) {
            JsonObject r = e.getAsJsonObject();
            if (!ruleMatches(r, features)) continue;
            allowed = r.get("action").getAsString().equals("allow");
        }
        return allowed;
    }

    private static boolean ruleMatches(JsonObject r, Set<String> features) {
        if (r.has("features")) {
            for (Map.Entry<String, JsonElement> f : r.getAsJsonObject("features").entrySet()) {
                if (f.getValue().getAsBoolean() != features.contains(f.getKey())) return false;
            }
        }
        if (r.has("os")) {
            JsonObject os = r.getAsJsonObject("os");
            if (os.has("name") && !os.get("name").getAsString().equals(OS_TYPE.mojang)) return false;
            if (os.has("arch")) {
                String a = os.get("arch").getAsString();
                if (a.equals("x86") && is64()) return false;
                if (a.equals("arm64") && !isArm()) return false;
            }
            if (os.has("version")) {
                Matcher m = Pattern.compile(os.get("version").getAsString())
                        .matcher(System.getProperty("os.version"));
                if (!m.find()) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ Sonstiges

    /** "group:artifact:version[:classifier][@ext]" -> Maven-Repository-Pfad */
    public static String mavenPath(String name) {
        String ext = "jar";
        int at = name.indexOf('@');
        if (at > 0) {
            ext = name.substring(at + 1);
            name = name.substring(0, at);
        }
        String[] p = name.split(":");
        String g = p[0].replace('.', '/'), a = p[1], v = p[2];
        String c = p.length > 3 ? "-" + p[3] : "";
        return g + "/" + a + "/" + v + "/" + a + "-" + v + c + "." + ext;
    }

    public static String str(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static final Pattern ARG = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    /** Zerlegt eine Argumentzeile; Anführungszeichen werden berücksichtigt. */
    public static List<String> splitArgs(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        Matcher m = ARG.matcher(s);
        while (m.find()) out.add(m.group(1) != null ? m.group(1) : m.group(2));
        return out;
    }

    public static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) s.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(p);
            }
        }
    }

    public static String cpSeparator() { return File.pathSeparator; }
}
