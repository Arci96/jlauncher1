package mclauncher;

import java.io.File;
import java.nio.file.Path;

/** Startet den Launcher neu (nur möglich, wenn er aus einer JAR-Datei läuft). */
public final class Restart {
    private Restart() {}

    public static boolean restart() {
        try {
            String jar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
            if (!jar.endsWith(".jar")) return false;
            String exe = Util.OS_TYPE == Util.OS.WINDOWS ? "javaw.exe" : "java";
            String java = Path.of(System.getProperty("java.home"), "bin", exe).toString();
            Store.save();
            Main.releaseLock();
            new ProcessBuilder(java, "-jar", jar).start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
