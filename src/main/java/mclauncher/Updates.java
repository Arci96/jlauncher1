package mclauncher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;

/** Prüft auf GitHub, ob es eine neuere Version des Launchers gibt. Es wird nichts automatisch installiert. */
public final class Updates {
    private Updates() {}

    public record Release(String tag, String version, String url, String notes) {}

    private static final Pattern REPO = Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9_.-]+");

    /** Version aus der JAR-Datei (Manifest); bei Entwicklungsläufen "0.0.0-dev". */
    public static String currentVersion() {
        Package p = Updates.class.getPackage();
        String v = p == null ? null : p.getImplementationVersion();
        return v == null || v.isBlank() ? "0.0.0-dev" : v;
    }

    public static boolean isDevBuild() { return currentVersion().endsWith("-dev"); }

    public static boolean validRepo(String repo) {
        if (repo == null || !REPO.matcher(repo.trim()).matches()) return false;
        String name = repo.trim().substring(repo.trim().indexOf('/') + 1);
        return !name.equals(".") && !name.equals("..");
    }

    public static Release latest(String repo) throws IOException {
        repo = repo == null ? "" : repo.trim();
        if (!validRepo(repo)) throw new IOException(I18n.t("Ungültiger Repository-Name (erwartet: Benutzer/Repository)."));
        JsonObject o;
        try {
            String body = Util.get("https://api.github.com/repos/" + repo + "/releases/latest",
                    Map.of("Accept", "application/vnd.github+json"));
            o = JsonParser.parseString(body).getAsJsonObject();
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                throw new IOException(I18n.t("Im Repository wurde kein Release gefunden. Prüfe den Namen und lege auf GitHub ein Release an."));
            }
            throw e;
        }
        String tag = Util.str(o, "tag_name");
        if (tag == null || tag.isBlank()) throw new IOException(I18n.t("Die Antwort von GitHub enthält keine Versionsnummer."));
        String url = Util.str(o, "html_url");
        // Nur Links auf github.com öffnen
        if (url == null || !"github.com".equals(URI.create(url).getHost())) url = "https://github.com/" + repo + "/releases";
        String notes = Util.str(o, "body");
        return new Release(tag, tag.replaceFirst("^[vV]", ""), url, notes == null ? "" : notes);
    }

    /** true, wenn {@code remote} eine höhere Versionsnummer hat als {@code local} (z. B. "1.2.0" > "1.1.9"). */
    public static boolean isNewer(String remote, String local) {
        int[] a = parse(remote), b = parse(local);
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return a[i] > b[i];
        }
        return false;
    }

    private static int[] parse(String v) {
        int[] n = new int[4];
        if (v == null) return n;
        v = v.trim().replaceFirst("^[vV]", "");
        int cut = v.indexOf('-');
        if (cut >= 0) v = v.substring(0, cut);
        String[] parts = v.split("\\.");
        for (int i = 0; i < Math.min(4, parts.length); i++) {
            String digits = parts[i].replaceAll("[^0-9]", "");
            if (!digits.isEmpty() && digits.length() < 9) n[i] = Integer.parseInt(digits);
        }
        return n;
    }
}
