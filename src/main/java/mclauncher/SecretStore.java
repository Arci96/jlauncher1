package mclauncher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Speichert Geheimnisse (z. B. das Microsoft-Token) nicht im Klartext, sondern mit den Mitteln des Systems:
 * <ul>
 *   <li>Windows: Windows-Datenschutz (DPAPI) über PowerShell - nur dein Benutzerkonto auf diesem PC kann es entschlüsseln</li>
 *   <li>macOS: Schlüsselbund über das Programm "security"</li>
 *   <li>Linux: Secret Service (GNOME Keyring, KWallet) über "secret-tool"</li>
 * </ul>
 * Geheimnisse werden nie in der Kommandozeile übergeben, sondern über die Standardeingabe.
 * Ist nichts davon verfügbar, meldet {@link #put} false und der Aufrufer entscheidet über einen Ersatz.
 */
public final class SecretStore {
    private SecretStore() {}

    public record Result(int code, String out, String err) {}

    @FunctionalInterface
    public interface Runner {
        Result run(List<String> cmd, String stdin) throws IOException;
    }

    private static final String ACCOUNT = "jlauncher";
    private static volatile Runner runner = SecretStore::exec;
    private static volatile Boolean available;

    /** Nur für Tests: eigene Befehlsausführung einsetzen. */
    public static void useRunner(Runner r) {
        runner = r;
        available = null;
    }

    public static synchronized boolean available() {
        if (available == null) available = detect();
        return available;
    }

    /** Lesbarer Name des verwendeten Speicherorts. */
    public static String backendLabel() {
        if (!available()) return I18n.t("launcher.json (kein Schlüsselbund gefunden)");
        return switch (Util.OS_TYPE) {
            case WINDOWS -> I18n.t("Windows-Datenschutz (DPAPI)");
            case MACOS -> I18n.t("macOS-Schlüsselbund");
            default -> I18n.t("Linux-Schlüsselbund (Secret Service)");
        };
    }

    // ------------------------------------------------------------------ Öffentliche Operationen

    /** Speichert den Wert und liest ihn zur Kontrolle zurück. Gibt true nur bei Erfolg zurück. */
    public static boolean put(String key, String value) {
        if (value == null || value.isBlank() || !available()) return false;
        try {
            boolean ok = switch (Util.OS_TYPE) {
                case WINDOWS -> putWindows(key, value);
                case MACOS -> putMac(key, value);
                default -> putLinux(key, value);
            };
            return ok && value.equals(get(key));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Liest einen Wert; null, wenn nicht vorhanden oder nicht lesbar. */
    public static String get(String key) {
        if (!available()) return null;
        try {
            String v = switch (Util.OS_TYPE) {
                case WINDOWS -> getWindows(key);
                case MACOS -> getMac(key);
                default -> getLinux(key);
            };
            return v == null || v.isEmpty() ? null : v;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static void delete(String key) {
        if (!available()) return;
        try {
            switch (Util.OS_TYPE) {
                case WINDOWS -> Files.deleteIfExists(winFile(key));
                case MACOS -> runner.run(List.of("security", "delete-generic-password", "-a", ACCOUNT, "-s", service(key)), null);
                default -> runner.run(List.of("secret-tool", "clear", "application", ACCOUNT, "key", key), null);
            }
        } catch (IOException | RuntimeException ignored) { }
    }

    // ------------------------------------------------------------------ Erkennung

    private static boolean detect() {
        try {
            switch (Util.OS_TYPE) {
                case WINDOWS -> {
                    return runner.run(ps("exit 0"), null).code() == 0;
                }
                case MACOS -> {
                    return runner.run(List.of("security", "list-keychains"), null).code() == 0;
                }
                default -> {
                    Result r = runner.run(List.of("secret-tool", "lookup", "application", ACCOUNT, "key", "probe"), null);
                    String e = r.err() == null ? "" : r.err().toLowerCase();
                    // Kein laufender Schlüsselbund-Dienst (z. B. ohne Desktop-Sitzung)
                    return !(e.contains("autolaunch") || e.contains("dbus") || e.contains("cannot") || e.contains("not provided"));
                }
            }
        } catch (IOException | RuntimeException e) {
            return false;   // Programm nicht vorhanden
        }
    }

    // ------------------------------------------------------------------ Windows (DPAPI)

    private static Path winFile(String key) {
        return AppPaths.ROOT.resolve("secrets").resolve(key.replaceAll("[^A-Za-z0-9._-]", "_") + ".dpapi");
    }

    private static List<String> ps(String script) {
        String enc = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc);
    }

    private static boolean putWindows(String key, String value) throws IOException {
        Result r = runner.run(ps("$t=[Console]::In.ReadToEnd().Trim(); "
                + "ConvertFrom-SecureString (ConvertTo-SecureString $t -AsPlainText -Force)"), value);
        String blob = r.out() == null ? "" : r.out().trim();
        if (r.code() != 0 || blob.isEmpty()) return false;
        Path f = winFile(key);
        Files.createDirectories(f.getParent());
        Files.writeString(f, blob, StandardCharsets.UTF_8);
        return true;
    }

    private static String getWindows(String key) throws IOException {
        Path f = winFile(key);
        if (!Files.exists(f)) return null;
        String blob = Files.readString(f, StandardCharsets.UTF_8).trim();
        Result r = runner.run(ps("$b=[Console]::In.ReadToEnd().Trim(); $s=ConvertTo-SecureString $b; "
                + "$p=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($s); "
                + "try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($p) } "
                + "finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($p) }"), blob);
        return r.code() == 0 && r.out() != null ? r.out().trim() : null;
    }

    // ------------------------------------------------------------------ macOS (security)

    private static String service(String key) { return "JLauncher." + key; }

    private static boolean putMac(String key, String value) throws IOException {
        // Der Wert wird über "security -i" von der Standardeingabe gelesen (nicht in der Prozessliste sichtbar).
        if (!value.matches("[\\x21-\\x7E&&[^\"\\\\]]+")) return false;
        Result r = runner.run(List.of("security", "-i"),
                "add-generic-password -a " + ACCOUNT + " -s " + service(key) + " -w \"" + value + "\" -U\n");
        return r.code() == 0;
    }

    private static String getMac(String key) throws IOException {
        Result r = runner.run(List.of("security", "find-generic-password", "-a", ACCOUNT, "-s", service(key), "-w"), null);
        return r.code() == 0 && r.out() != null ? r.out().trim() : null;
    }

    // ------------------------------------------------------------------ Linux (secret-tool)

    private static boolean putLinux(String key, String value) throws IOException {
        Result r = runner.run(List.of("secret-tool", "store", "--label=JLauncher " + key, "application", ACCOUNT, "key", key), value);
        return r.code() == 0;
    }

    private static String getLinux(String key) throws IOException {
        Result r = runner.run(List.of("secret-tool", "lookup", "application", ACCOUNT, "key", key), null);
        return r.code() == 0 && r.out() != null ? r.out().trim() : null;
    }

    // ------------------------------------------------------------------ Prozessausführung

    private static Result exec(List<String> cmd, String stdin) throws IOException {
        Process p = new ProcessBuilder(cmd).start();
        try (OutputStream os = p.getOutputStream()) {
            if (stdin != null) os.write(stdin.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // Programm hat die Eingabe nicht abgewartet - das Ergebnis zeigt der Exit-Code
        }
        // Ausgaben parallel lesen, damit ein voller Puffer den Prozess nicht blockiert
        CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> readAll(p.getInputStream()));
        CompletableFuture<String> err = CompletableFuture.supplyAsync(() -> readAll(p.getErrorStream()));
        try {
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException(I18n.t("Zeitüberschreitung"));
            }
            return new Result(p.exitValue(), out.get(3, TimeUnit.SECONDS), err.get(3, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IOException(e);
        }
    }

    private static String readAll(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
