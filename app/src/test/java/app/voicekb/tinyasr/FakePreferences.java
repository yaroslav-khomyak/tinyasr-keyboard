package app.voicekb.tinyasr;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** In-memory SharedPreferences for tests. */
final class FakePreferences implements SharedPreferences {
    final Map<String, Object> values = new HashMap<>();

    @Override public Map<String, ?> getAll() { return values; }
    @Override public String getString(String k, String d) { return values.containsKey(k) ? (String) values.get(k) : d; }
    @SuppressWarnings("unchecked")
    @Override public Set<String> getStringSet(String k, Set<String> d) { return values.containsKey(k) ? (Set<String>) values.get(k) : d; }
    @Override public int getInt(String k, int d) { return values.containsKey(k) ? (Integer) values.get(k) : d; }
    @Override public long getLong(String k, long d) { return values.containsKey(k) ? (Long) values.get(k) : d; }
    @Override public float getFloat(String k, float d) { return values.containsKey(k) ? (Float) values.get(k) : d; }
    @Override public boolean getBoolean(String k, boolean d) { return values.containsKey(k) ? (Boolean) values.get(k) : d; }
    @Override public boolean contains(String k) { return values.containsKey(k); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

    @Override
    public Editor edit() {
        return new Editor() {
            @Override public Editor putString(String k, String v) { values.put(k, v); return this; }
            @Override public Editor putStringSet(String k, Set<String> v) { values.put(k, v); return this; }
            @Override public Editor putInt(String k, int v) { values.put(k, v); return this; }
            @Override public Editor putLong(String k, long v) { values.put(k, v); return this; }
            @Override public Editor putFloat(String k, float v) { values.put(k, v); return this; }
            @Override public Editor putBoolean(String k, boolean v) { values.put(k, v); return this; }
            @Override public Editor remove(String k) { values.remove(k); return this; }
            @Override public Editor clear() { values.clear(); return this; }
            @Override public boolean commit() { return true; }
            @Override public void apply() {}
        };
    }
}
