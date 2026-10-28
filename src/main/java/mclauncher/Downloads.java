package mclauncher;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Verwaltet alle Hintergrund-Aufgaben (Downloads, Installationen, Spielstart) für die Download-Ansicht. */
public final class Downloads {
    private Downloads() {}

    public enum State { RUNNING, DONE, FAILED, CANCELLED }

    public static final class Task {
        public final String title;
        public volatile String message = I18n.t("Wird gestartet …");
        public volatile double fraction = -1;   // < 0 = unbestimmt
        public volatile State state = State.RUNNING;
        public volatile boolean cancelRequested;
        private final long startBytes = Util.BYTES.get();
        private volatile long endBytes = -1;

        Task(String title) { this.title = title; }

        /** Ungefähr geladene Bytes (Zähler wird zwischen parallelen Aufgaben geteilt). */
        public long bytes() {
            return Math.max(0, (endBytes >= 0 ? endBytes : Util.BYTES.get()) - startBytes);
        }
    }

    @FunctionalInterface
    public interface Body {
        void run(Util.Progress progress) throws Exception;
    }

    private static final List<Task> TASKS = new CopyOnWriteArrayList<>();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile double speed;   // Bytes pro Sekunde

    static {
        Thread t = new Thread(() -> {
            long last = Util.BYTES.get();
            while (true) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
                long cur = Util.BYTES.get();
                speed = (cur - last) * 2.0;
                last = cur;
            }
        }, "download-speed");
        t.setDaemon(true);
        t.start();
    }

    public static List<Task> tasks() { return TASKS; }

    public static double speedMbPerSec() { return speed / (1024.0 * 1024.0); }

    public static int active() {
        int n = 0;
        for (Task t : TASKS) if (t.state == State.RUNNING) n++;
        return n;
    }

    public static void addListener(Runnable r) { LISTENERS.add(r); }

    private static void fire() {
        for (Runnable r : LISTENERS) r.run();
    }

    public static void cancel(Task t) {
        t.cancelRequested = true;
        fire();
    }

    public static void clearFinished() {
        TASKS.removeIf(t -> t.state != State.RUNNING);
        fire();
    }

    /**
     * Startet eine Aufgabe im Hintergrund. {@code done} wird danach aufgerufen:
     * mit null bei Erfolg, sonst mit dem Fehler (CancellationException bei Abbruch).
     */
    public static Task start(String title, Body body, Consumer<Throwable> done) {
        Task task = new Task(title);
        TASKS.add(task);
        while (TASKS.size() > 30) {
            Task old = TASKS.stream().filter(x -> x.state != State.RUNNING).findFirst().orElse(null);
            if (old == null) break;
            TASKS.remove(old);
        }
        fire();

        Util.async(() -> {
            Throwable err = null;
            try {
                body.run((msg, f) -> {
                    if (task.cancelRequested) throw new CancellationException(I18n.t("Abgebrochen"));
                    task.message = msg;
                    task.fraction = f;
                    fire();
                });
                task.state = State.DONE;
                task.fraction = 1;
                task.message = I18n.t("Abgeschlossen");
            } catch (Throwable e) {
                if (task.cancelRequested || e instanceof CancellationException) {
                    task.state = State.CANCELLED;
                    task.message = I18n.t("Abgebrochen");
                    err = new CancellationException(I18n.t("Abgebrochen"));
                } else {
                    task.state = State.FAILED;
                    task.message = e.getMessage() != null ? e.getMessage() : e.toString();
                    err = e;
                }
            } finally {
                task.endBytes = Util.BYTES.get();
                fire();
            }
            if (done != null) done.accept(err);
        });
        return task;
    }
}
