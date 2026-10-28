package mclauncher;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Einfache Zweisprachigkeit: Deutsche Texte sind die Schlüssel, Englisch kommt aus Translations. */
public final class I18n {
    private I18n() {}

    private static volatile boolean english;
    private static final Map<String, String> EN = new HashMap<>();

    static {
        String[] p = Translations.PAIRS;
        for (int i = 0; i + 1 < p.length; i += 2) EN.put(p[i], p[i + 1]);
    }

    /** Muss nach Store.load() und vor dem Aufbau der Oberfläche aufgerufen werden. */
    public static void init() {
        String c = Store.data.config.language;
        if (c == null || c.isBlank()) c = "de".equals(Locale.getDefault().getLanguage()) ? "de" : "en";
        english = "en".equals(c);
        Locale.setDefault(english ? Locale.ENGLISH : Locale.GERMAN);
    }

    public static boolean english() { return english; }

    public static String code() { return english ? "en" : "de"; }

    public static String t(String de) {
        if (!english || de == null) return de;
        String v = EN.get(de);
        return v != null ? v : de;
    }
}
