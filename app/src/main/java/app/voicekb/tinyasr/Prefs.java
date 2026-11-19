package app.voicekb.tinyasr;

import android.content.Context;
import android.content.SharedPreferences;

/** Small settings store. */
final class Prefs {
    /** Language modes, in the order the language key cycles through them. */
    static final String[] MODES = {"uk", "en", "uk,en"};
    private static final String[] LABELS = {"UK", "EN", "UK+EN"};

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("voice", Context.MODE_PRIVATE);
    }

    int mode() {
        int m = sp.getInt("mode", 0);
        return m >= 0 && m < MODES.length ? m : 0;
    }

    void setMode(int m) {
        sp.edit().putInt("mode", m).apply();
    }

    static String label(int mode) {
        return LABELS[mode];
    }

    /** With several languages: the one the recognizer ended on last time. */
    String lastLang() {
        return sp.getString("lastLang", "uk");
    }

    void setLastLang(String lang) {
        if ("uk".equals(lang) || "en".equals(lang)) sp.edit().putString("lastLang", lang).apply();
    }

    boolean autoStart() {
        return sp.getBoolean("autoStart", false);
    }

    void setAutoStart(boolean v) {
        sp.edit().putBoolean("autoStart", v).apply();
    }
}
