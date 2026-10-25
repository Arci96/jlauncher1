package mclauncher;

import com.formdev.flatlaf.FlatDarkLaf;
import mclauncher.ui.MainWindow;
import mclauncher.ui.Theme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private static FileLock lock;   // Referenz halten, sonst wird die Sperre freigegeben
    private static final AtomicBoolean CRASH_DIALOG_OPEN = new AtomicBoolean();

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> reportCrash(e));

        FlatDarkLaf.setup();
        Theme.install();
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ProgressBar.arc", 12);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("TabbedPane.showTabSeparators", true);

        if (!acquireLock()) {
            JOptionPane.showMessageDialog(null, "JLauncher läuft bereits.\nBitte nur ein Fenster gleichzeitig verwenden.",
                    "JLauncher", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
        }

        Store.load();
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }

    /** Verhindert, dass zwei Launcher gleichzeitig die Konfiguration überschreiben. */
    private static boolean acquireLock() {
        try {
            Files.createDirectories(AppPaths.ROOT);
            FileChannel ch = FileChannel.open(AppPaths.ROOT.resolve("launcher.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = ch.tryLock();
            return lock != null;
        } catch (OverlappingFileLockException e) {
            return false;
        } catch (IOException e) {
            return true;   // Sperre nicht möglich - trotzdem starten
        }
    }

    /** Schreibt unerwartete Fehler in launcher-crash.log und zeigt einmalig eine Meldung. */
    private static void reportCrash(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        try {
            Files.createDirectories(AppPaths.ROOT);
            Files.writeString(AppPaths.ROOT.resolve("launcher-crash.log"),
                    "==== " + LocalDateTime.now() + " ====\n" + sw + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
        e.printStackTrace();
        if (CRASH_DIALOG_OPEN.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                        "Ein unerwarteter Fehler ist aufgetreten:\n" + e + "\n\nDetails stehen in:\n"
                                + AppPaths.ROOT.resolve("launcher-crash.log"),
                        "JLauncher – Fehler", JOptionPane.ERROR_MESSAGE);
                CRASH_DIALOG_OPEN.set(false);
            });
        }
    }
}
