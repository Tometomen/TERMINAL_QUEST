package jp.terminalquest.save;

import com.badlogic.gdx.Preferences;
import java.util.HashMap;
import java.util.Map;

/** A real value store for save tests, with no host filesystem dependency. */
public class MemoryPreferences implements Preferences {
    private final Map<String, Object> values = new HashMap<String, Object>();
    public boolean failFlush;
    public Preferences putBoolean(String key, boolean value) { values.put(key, value); return this; }
    public Preferences putInteger(String key, int value) { values.put(key, value); return this; }
    public Preferences putLong(String key, long value) { values.put(key, value); return this; }
    public Preferences putFloat(String key, float value) { values.put(key, value); return this; }
    public Preferences putString(String key, String value) { values.put(key, value); return this; }
    public Preferences put(Map<String, ?> values) { this.values.putAll(values); return this; }
    public boolean getBoolean(String key) { return getBoolean(key, false); }
    public int getInteger(String key) { return getInteger(key, 0); }
    public long getLong(String key) { return getLong(key, 0); }
    public float getFloat(String key) { return getFloat(key, 0); }
    public String getString(String key) { return getString(key, ""); }
    public boolean getBoolean(String key, boolean defaultValue) { return values.containsKey(key) ? (Boolean) values.get(key) : defaultValue; }
    public int getInteger(String key, int defaultValue) { return values.containsKey(key) ? (Integer) values.get(key) : defaultValue; }
    public long getLong(String key, long defaultValue) { return values.containsKey(key) ? (Long) values.get(key) : defaultValue; }
    public float getFloat(String key, float defaultValue) { return values.containsKey(key) ? (Float) values.get(key) : defaultValue; }
    public String getString(String key, String defaultValue) { return values.containsKey(key) ? (String) values.get(key) : defaultValue; }
    public Map<String, ?> get() { return new HashMap<String, Object>(values); }
    public boolean contains(String key) { return values.containsKey(key); }
    public void clear() { values.clear(); }
    public void remove(String key) { values.remove(key); }
    public void flush() { if (failFlush) throw new IllegalStateException("Storage denied"); }
}
