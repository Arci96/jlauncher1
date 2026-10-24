package mclauncher.ui;

import mclauncher.Instance;
import mclauncher.Store;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Gemeinsamer Zustand der Oberfläche: das aktuell gewählte Profil. */
public final class AppState {
    private AppState() {}

    private static Instance current;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    public static Instance current() {
        if (current != null && Store.data.instances.contains(current)) return current;
        String last = Store.data.config.lastProfile;
        for (Instance i : Store.data.instances) {
            if (i.id.equals(last)) return current = i;
        }
        return current = Store.data.instances.isEmpty() ? null : Store.data.instances.get(0);
    }

    public static void setCurrent(Instance i) {
        current = i;
        if (i != null) {
            Store.data.config.lastProfile = i.id;
            Store.save();
        }
        fire();
    }

    public static void addListener(Runnable r) { LISTENERS.add(r); }

    /** Muss im Swing-Thread aufgerufen werden. */
    public static void fire() {
        for (Runnable r : LISTENERS) r.run();
    }
}
