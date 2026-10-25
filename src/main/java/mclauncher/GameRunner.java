package mclauncher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Startet und überwacht das Spiel; hält den Konsolen-Verlauf. */
public final class GameRunner {
    private GameRunner() {}

    public enum State { IDLE, PREPARING, RUNNING }

    private static volatile State state = State.IDLE;
    private static volatile Process process;
    private static volatile Instance running;
    private static volatile Downloads.Task prepTask;
    private static volatile int lastExit;
    private static volatile boolean stoppedByUser;
    private static volatile Instance lastInstance;

    private static final List<Runnable> STATE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<Consumer<String>> LOG_LISTENERS = new CopyOnWriteArrayList<>();
    private static final Deque<String> HISTORY = new ArrayDeque<>();

    public static State state() { return state; }
    public static Instance running() { return running; }
    public static int lastExitCode() { return lastExit; }
    public static boolean stoppedByUser() { return stoppedByUser; }
    public static Instance lastInstance() { return lastInstance; }

    public static void addStateListener(Runnable r) { STATE_LISTENERS.add(r); }
    public static void addLogListener(Consumer<String> c) { LOG_LISTENERS.add(c); }

    public static List<String> history() {
        synchronized (HISTORY) {
            return new ArrayList<>(HISTORY);
        }
    }

    public static void clearHistory() {
        synchronized (HISTORY) {
            HISTORY.clear();
        }
    }

    public static void log(String line) {
        synchronized (HISTORY) {
            HISTORY.addLast(line);
            while (HISTORY.size() > 5000) HISTORY.removeFirst();
        }
        for (Consumer<String> c : LOG_LISTENERS) c.accept(line);
    }

    private static void setState(State s) {
        state = s;
        for (Runnable r : STATE_LISTENERS) r.run();
    }

    /** Bereitet alles vor und startet das Spiel (optional mit direktem Serverbeitritt). */
    public static synchronized void play(Instance inst, String server, Consumer<Throwable> onError) {
        if (state != State.IDLE) return;
        running = inst;
        lastInstance = inst;
        stoppedByUser = false;
        lastExit = 0;
        setState(State.PREPARING);
        log("──── Start: " + inst.name + " (" + inst.label() + ") ────");

        prepTask = Downloads.start("Minecraft " + inst.mcVersion + " starten (" + inst.name + ")", p -> {
            Auth.Account acc = Auth.current();
            log("Konto: " + acc.name() + " (" + acc.userType() + ")");
            Process pr = Launcher.start(inst, acc, server, p, GameRunner::log);
            process = pr;
            inst.lastPlayed = System.currentTimeMillis();
            Store.save();
            setState(State.RUNNING);
            Util.async(() -> {
                int code = -1;
                try {
                    code = pr.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                lastExit = code;
                log("Minecraft beendet (Exit-Code " + code + ")");
                process = null;
                running = null;
                setState(State.IDLE);
            });
        }, err -> {
            if (err == null) return;
            log(err instanceof CancellationException ? "Start abgebrochen." : "FEHLER: " + err.getMessage());
            process = null;
            running = null;
            setState(State.IDLE);
            if (!(err instanceof CancellationException) && onError != null) onError.accept(err);
        });
    }

    /** Beendet das laufende Spiel bzw. bricht die Vorbereitung ab. */
    public static void stop() {
        Process p = process;
        if (p != null) {
            stoppedByUser = true;
            p.destroy();
            return;
        }
        Downloads.Task t = prepTask;
        if (t != null && state == State.PREPARING) Downloads.cancel(t);
    }
}
