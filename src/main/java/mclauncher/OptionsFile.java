package mclauncher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Liest und ändert einzelne Einträge in Minecrafts options.txt (z. B. maxFps). */
public final class OptionsFile {
    private OptionsFile() {}

    public static void set(Path gameDir, String key, String value) throws IOException {
        Files.createDirectories(gameDir);
        Path f = gameDir.resolve("options.txt");
        List<String> lines = Files.exists(f) ? new ArrayList<>(Files.readAllLines(f, StandardCharsets.UTF_8)) : new ArrayList<>();
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(key + ":")) {
                lines.set(i, key + ":" + value);
                found = true;
            }
        }
        if (!found) lines.add(key + ":" + value);
        Files.write(f, lines, StandardCharsets.UTF_8);
    }
}
