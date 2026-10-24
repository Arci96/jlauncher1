package mclauncher;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Sucht installierte Java-Versionen und wählt die passende für eine Minecraft-Version. */
public final class JavaFinder {
    private JavaFinder() {}

    public record Found(Path home, int major) {}

    private static final Pattern VERSION = Pattern.compile("JAVA_VERSION=\"(\\d+)(?:\\.(\\d+))?");

    public static List<Found> all() {
        Set<Path> homes = new LinkedHashSet<>();
        homes.add(Path.of(System.getProperty("java.home")));
        String env = System.getenv("JAVA_HOME");
        if (env != null && !env.isBlank()) homes.add(Path.of(env));

        for (Path root : roots()) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> s = Files.list(root)) {
                s.forEach(child -> {
                    homes.add(child);
                    homes.add(child.resolve("Contents").resolve("Home"));   // macOS
                });
            } catch (IOException ignored) { }
        }

        List<Found> out = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        for (Path h : homes) {
            if (!Files.exists(exe(h))) continue;
            Path real;
            try { real = h.toRealPath(); } catch (IOException e) { real = h; }
            if (!seen.add(real)) continue;
            int major = major(h);
            if (major > 0) out.add(new Found(h, major));
        }
        return out;
    }

    private static List<Path> roots() {
        String home = System.getProperty("user.home");
        List<Path> r = new ArrayList<>();
        r.add(AppPaths.ROOT.resolve("runtimes"));
        switch (Util.OS_TYPE) {
            case WINDOWS -> {
                for (String var : new String[]{"ProgramFiles", "ProgramFiles(x86)"}) {
                    String pf = System.getenv(var);
                    if (pf == null) continue;
                    for (String d : new String[]{"Java", "Eclipse Adoptium", "Microsoft", "Zulu", "BellSoft", "Amazon Corretto", "Semeru"}) {
                        r.add(Path.of(pf, d));
                    }
                }
            }
            case MACOS -> {
                r.add(Path.of("/Library/Java/JavaVirtualMachines"));
                r.add(Path.of(home, "Library", "Java", "JavaVirtualMachines"));
            }
            default -> {
                r.add(Path.of("/usr/lib/jvm"));
                r.add(Path.of("/usr/java"));
                r.add(Path.of(home, ".sdkman", "candidates", "java"));
                r.add(Path.of(home, ".jdks"));
            }
        }
        return r;
    }

    public static Path exe(Path home) {
        if (Util.OS_TYPE == Util.OS.WINDOWS) {
            Path w = home.resolve("bin").resolve("javaw.exe");
            return Files.exists(w) ? w : home.resolve("bin").resolve("java.exe");
        }
        return home.resolve("bin").resolve("java");
    }

    private static int major(Path home) {
        Path release = home.resolve("release");
        if (home.equals(Path.of(System.getProperty("java.home")))) return Runtime.version().feature();
        try {
            Matcher m = VERSION.matcher(Files.readString(release));
            if (m.find()) {
                int a = Integer.parseInt(m.group(1));
                if (a == 1 && m.group(2) != null) return Integer.parseInt(m.group(2));   // 1.8 -> 8
                return a;
            }
        } catch (IOException | NumberFormatException ignored) { }
        return -1;
    }

    /** Pfad zur java-Datei mit exakt passender Version; sonst nächsthöhere (nur wenn >= 17), sonst null. */
    public static String find(int required) {
        List<Found> all = all();
        for (Found f : all) if (f.major() == required) return exe(f.home()).toString();
        if (required >= 17) {
            return all.stream().filter(f -> f.major() > required)
                    .min(Comparator.comparingInt(Found::major))
                    .map(f -> exe(f.home()).toString()).orElse(null);
        }
        return null;
    }
}
