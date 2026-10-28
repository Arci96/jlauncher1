package mclauncher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Welten eines Profils: auflisten, sichern, wiederherstellen, löschen. Backups liegen außerhalb des Profilordners. */
public final class Worlds {
    private Worlds() {}

    public record World(Path dir, String name, long modified, long size) {}

    public static Path savesDir(Instance inst) { return inst.dir().resolve("saves"); }

    public static Path backupDir(Instance inst) { return AppPaths.ROOT.resolve("backups").resolve(inst.id); }

    public static List<World> list(Instance inst) throws IOException {
        Path saves = savesDir(inst);
        List<World> out = new ArrayList<>();
        if (!Files.isDirectory(saves)) return out;
        try (Stream<Path> s = Files.list(saves)) {
            for (Path d : (Iterable<Path>) s::iterator) {
                Path level = d.resolve("level.dat");
                if (Files.isDirectory(d) && Files.exists(level)) {
                    out.add(new World(d, d.getFileName().toString(), Files.getLastModifiedTime(level).toMillis(), size(d)));
                }
            }
        }
        out.sort(Comparator.comparingLong(World::modified).reversed());
        return out;
    }

    public static List<Path> backups(Instance inst) throws IOException {
        Path dir = backupDir(inst);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed()).toList();
        }
    }

    public static long size(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return 0; }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    public static Path backup(Instance inst, World w, Util.Progress p) throws IOException {
        Path dir = backupDir(inst);
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path zip = dir.resolve(w.name().replaceAll("[\\\\/:*?\"<>|]", "_") + "_" + stamp + ".zip");

        List<Path> files;
        try (Stream<Path> s = Files.walk(w.dir())) {
            files = s.filter(Files::isRegularFile).filter(f -> !f.getFileName().toString().equals("session.lock")).toList();
        }
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            int i = 0, n = files.size();
            for (Path f : files) {
                String entry = w.name() + "/" + w.dir().relativize(f).toString().replace('\\', '/');
                ZipEntry ze = new ZipEntry(entry);
                ze.setTime(Files.getLastModifiedTime(f).toMillis());
                out.putNextEntry(ze);
                Files.copy(f, out);
                out.closeEntry();
                if (++i % 25 == 0 || i == n) p.update(I18n.t("Sichere \"") + w.name() + "\" (" + i + "/" + n + ")", (double) i / n);
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(zip);
            throw e;
        }
        return zip;
    }

    /** Stellt ein Backup als neue Welt wieder her; eine vorhandene Welt wird nie überschrieben. */
    public static Path restore(Instance inst, Path zip, Util.Progress p) throws IOException {
        Path saves = savesDir(inst);
        Files.createDirectories(saves);
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            String root = null;
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements() && root == null) {
                String n = en.nextElement().getName();
                int i = n.indexOf('/');
                if (i > 0) root = n.substring(0, i);
            }
            if (root == null) throw new IOException(I18n.t("Das Backup ist leer oder beschädigt."));

            String name = root;
            int k = 0;
            while (Files.exists(saves.resolve(name))) {
                k++;
                name = root + "_wiederhergestellt" + (k > 1 ? "_" + k : "");
            }
            Path dest = saves.resolve(name).normalize();

            p.update(I18n.t("Stelle \"") + name + I18n.t("\" wieder her …"), -1);
            en = zf.entries();
            try {
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName();
                    if (e.isDirectory() || !n.startsWith(root + "/")) continue;
                    Path target = dest.resolve(n.substring(root.length() + 1)).normalize();
                    if (!target.startsWith(dest)) continue;   // Zip-Slip-Schutz
                    Files.createDirectories(target.getParent());
                    try (InputStream in = zf.getInputStream(e)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (IOException | RuntimeException ex) {
                Util.deleteRecursive(dest);
                throw ex;
            }
            return dest;
        }
    }

    public static void delete(Instance inst, World w) throws IOException {
        Path saves = savesDir(inst).toAbsolutePath().normalize();
        Path d = w.dir().toAbsolutePath().normalize();
        if (!d.startsWith(saves) || d.equals(saves)) throw new IOException(I18n.t("Unzulässiger Pfad."));
        Util.deleteRecursive(d);
    }
}
