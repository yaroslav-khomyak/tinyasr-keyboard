package app.voicekb.tinyasr;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Small settings store. */
final class Prefs {
    /** The model's languages, in the order the language key shows them. */
    static final String[] LANGS = {"uk", "en", "ru"};

    private final SharedPreferences sp;

    Prefs(Context c) {
        this(c.getApplicationContext().getSharedPreferences("voice", Context.MODE_PRIVATE));
    }

    Prefs(SharedPreferences sp) {
        this.sp = sp;
    }

    boolean langEnabled(String lang) {
        return sp.getBoolean("lang_" + lang, true);
    }

    /** Refuses to turn off the last language; returns whether the change was made. */
    boolean setLangEnabled(String lang, boolean on) {
        if (!on && langEnabled(lang) && enabledLangs().size() <= 1) return false;
        sp.edit().putBoolean("lang_" + lang, on).apply();
        return true;
    }

    List<String> enabledLangs() {
        List<String> l = new ArrayList<>();
        for (String s : LANGS) if (langEnabled(s)) l.add(s);
        if (l.isEmpty()) l.add(LANGS[0]);
        return l;
    }

    /** Offer a mode in which the recognizer picks among the enabled languages. */
    boolean autoMode() {
        return sp.getBoolean("autoMode", true);
    }

    void setAutoMode(boolean v) {
        sp.edit().putBoolean("autoMode", v).apply();
    }

    /**
     * What the language key cycles through: each enabled language, then (with several, if wanted) all of them at
     * once. A mode is the comma-separated list the recognizer takes.
     */
    List<String> modes() {
        List<String> m = enabledLangs();
        if (m.size() > 1 && autoMode()) m.add(String.join(",", m));
        return m;
    }

    /** The selected mode; the first one if the stored one is no longer offered. */
    String mode() {
        List<String> m = modes();
        String cur = sp.getString("modeLangs", null);
        return cur != null && m.contains(cur) ? cur : m.get(0);
    }

    void setMode(String mode) {
        sp.edit().putString("modeLangs", mode).apply();
    }

    String nextMode() {
        List<String> m = modes();
        return m.get((m.indexOf(mode()) + 1) % m.size());
    }

    static boolean isAuto(String mode) {
        return mode.indexOf(',') >= 0;
    }

    static String label(String mode) {
        return isAuto(mode) ? "AUTO" : mode.toUpperCase(java.util.Locale.ROOT);
    }

    /** With several languages: the one to assume first (where the recognizer ended last time, if still allowed). */
    String startLang(String mode) {
        String last = sp.getString("lastLang", null);
        String[] langs = mode.split(",");
        for (String l : langs) if (l.equals(last)) return l;
        return langs[0];
    }

    void setLastLang(String lang) {
        for (String l : LANGS) if (l.equals(lang)) sp.edit().putString("lastLang", lang).apply();
    }

    boolean autoStart() {
        return sp.getBoolean("autoStart", false);
    }

    void setAutoStart(boolean v) {
        sp.edit().putBoolean("autoStart", v).apply();
    }
}
