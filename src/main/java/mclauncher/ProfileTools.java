package mclauncher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** Hilfen für Profile: Kopieren. */
public final class ProfileTools {
    private ProfileTools() {}

    /** Erzeugt eine Kopie des Profils samt Ordner (Welten, Mods, Einstellungen). */
    public static Instance duplicate(Instance src, Util.Progress p) throws IOException {
        Instance copy = Util.GSON.fromJson(Util.GSON.toJson(src), Instance.class);
        copy.id = UUID.randomUUID().toString().substring(0, 8);
        copy.name = src.name + I18n.t(" (Kopie)");
        copy.gameDirOverride = "";
        copy.lastPlayed = 0;

        Path from = src.dir().toAbsolutePath().normalize();
        Path to = copy.dir().toAbsolutePath().normalize();
        if (to.startsWith(from)) throw new IOException(I18n.t("Der Zielordner liegt im Quellordner."));

        try {
            Files.createDirectories(to);
            if (Files.isDirectory(from)) {
                List<Path> all;
                try (Stream<Path> s = Files.walk(from)) {
                    all = s.toList();
                }
                int n = all.size(), i = 0;
                for (Path path : all) {
                    Path target = to.resolve(from.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else if (!path.getFileName().toString().equals("session.lock")) {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    if (++i % 20 == 0 || i == n) p.update(I18n.t("Kopiere Dateien (") + i + "/" + n + ")", (double) i / n);
                }
            }
        } catch (IOException | RuntimeException e) {
            try { Util.deleteRecursive(to); } catch (IOException ignored) { }
            throw e;
        }
        return copy;
    }
}
