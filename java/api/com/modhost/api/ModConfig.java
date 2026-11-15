package com.modhost.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Per-mod settings. Declare options in onLoad; they appear under "Settings" in the in-game menu and are
 * saved to <dataDir>/<id>/config.properties. Changes raise the event "config.changed" with "key=value".
 */
public final class ModConfig {
    public static final int TOGGLE = 0, NUMBER = 1, TEXT = 2;

    public static final class Option {
        public final String key, label, def;
        public final int type;
        public final double min, max;
        Option(String key, String label, int type, String def, double min, double max) {
            this.key = key; this.label = label; this.type = type; this.def = def; this.min = min; this.max = max;
        }
    }

    private final File file;
    private final Properties props = new Properties();
    private final List<Option> options = new ArrayList<Option>();
    private final Map<String, Option> byKey = new HashMap<String, Option>();

    ModConfig(File dir) {
        dir.mkdirs();
        file = new File(dir, "config.properties");
        if (file.exists()) {
            try {
                FileInputStream in = new FileInputStream(file);
                try { props.load(in); } finally { in.close(); }
            } catch (Throwable t) { new ModLogger("ModHost").warn("could not read " + file + ": " + t); }
        }
    }

    public ModConfig toggle(String key, String label, boolean def) {
        return add(new Option(key, label, TOGGLE, String.valueOf(def), 0, 1));
    }

    public ModConfig number(String key, String label, double def, double min, double max) {
        if (max <= min) max = min + 1;
        return add(new Option(key, label, NUMBER, String.valueOf(def), min, max));
    }

    public ModConfig text(String key, String label, String def) {
        return add(new Option(key, label, TEXT, def, 0, 0));
    }

    public boolean getBoolean(String key) { return Boolean.parseBoolean(raw(key)); }

    public double getDouble(String key) {
        try { return Double.parseDouble(raw(key)); } catch (NumberFormatException e) { return 0; }
    }

    public String getString(String key) { return raw(key); }

    List<Option> options() { return options; }

    void set(String key, String value) {
        props.setProperty(key, value);
        try {
            FileOutputStream out = new FileOutputStream(file);
            try { props.store(out, "ModHost settings"); } finally { out.close(); }
        } catch (Throwable t) { new ModLogger("ModHost").warn("could not save " + file + ": " + t); }
        Events.post("config.changed", key + "=" + value);
    }

    private ModConfig add(Option o) {
        if (!byKey.containsKey(o.key)) { options.add(o); byKey.put(o.key, o); }
        return this;
    }

    private String raw(String key) {
        Option o = byKey.get(key);
        return props.getProperty(key, o == null ? "" : o.def);
    }
}
