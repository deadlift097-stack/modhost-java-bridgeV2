package com.modhost.bedrock;

import com.modhost.api.Host;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Bedrock's options.txt ("key:value" per line). Bedrock reads it at start-up, so a change is applied on the
 * next launch. set() only changes keys that already exist, so a mod can never invent an option.
 */
public final class Options {
    static final Options INSTANCE = new Options();

    private Options() {}

    static File findGameDir() {
        String root = Host.dataRoot();
        if (root == null || root.length() == 0) return null;
        for (File d = new File(root); d != null; d = d.getParentFile()) {
            String[] rel = { "", "games/com.mojang", "files/games/com.mojang", "minecraftpe" };
            for (String r : rel) {
                File c = r.length() == 0 ? d : new File(d, r);
                if (new File(c, "options.txt").isFile()) return c;
            }
        }
        return null;
    }

    private File file() {
        File d = findGameDir();
        return d == null ? null : new File(d, "options.txt");
    }

    public boolean available() { return file() != null; }

    public boolean has(String key) { return get(key) != null; }

    /** Value of an option, or null if it does not exist. */
    public String get(String key) {
        for (String l : read()) {
            int i = l.indexOf(':');
            if (i > 0 && l.substring(0, i).equals(key)) return l.substring(i + 1);
        }
        return null;
    }

    public int getInt(String key, int def) {
        try { return Integer.parseInt(get(key).trim()); } catch (Throwable t) { return def; }
    }

    /** Changes an existing option. Returns false if the file or key is missing or the write failed. */
    public boolean set(String key, String value) {
        File f = file();
        if (f == null) return false;
        List<String> ls = read();
        boolean hit = false;
        for (int i = 0; i < ls.size(); i++) {
            String l = ls.get(i);
            int c = l.indexOf(':');
            if (c > 0 && l.substring(0, c).equals(key)) { ls.set(i, key + ":" + value); hit = true; }
        }
        if (!hit) return false;
        try {
            FileWriter w = new FileWriter(f);
            try { for (String l : ls) w.write(l + "\n"); } finally { w.close(); }
            return true;
        } catch (Throwable t) { return false; }
    }

    private List<String> read() {
        List<String> out = new ArrayList<String>();
        File f = file();
        if (f == null) return out;
        try {
            BufferedReader r = new BufferedReader(new FileReader(f));
            try { String l; while ((l = r.readLine()) != null) out.add(l); } finally { r.close(); }
        } catch (Throwable ignored) {}
        return out;
    }
}
