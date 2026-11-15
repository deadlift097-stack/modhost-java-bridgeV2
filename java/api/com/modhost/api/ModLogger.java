package com.modhost.api;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public final class ModLogger {
    public static final int DEBUG = 0, INFO = 1, WARN = 2, ERROR = 3;
    private static final LinkedList<String> BUFFER = new LinkedList<String>();
    private final String prefix;

    public ModLogger(String prefix) { this.prefix = prefix; }

    public void debug(String m) { emit(DEBUG, m); }
    public void info(String m)  { emit(INFO, m); }
    public void warn(String m)  { emit(WARN, m); }
    public void error(String m) { emit(ERROR, m); }
    public void log(int level, String m) { emit(level, m); }

    /** Last n log lines (used by the in-game menu). */
    public static List<String> recent(int n) {
        synchronized (BUFFER) {
            int from = Math.max(0, BUFFER.size() - n);
            return new ArrayList<String>(BUFFER.subList(from, BUFFER.size()));
        }
    }

    private void emit(int level, String m) {
        String line = "[" + prefix + "] " + m;
        synchronized (BUFFER) {
            BUFFER.add(line);
            while (BUFFER.size() > 200) BUFFER.removeFirst();
        }
        try {
            NativeBridge.log(level, line);
        } catch (UnsatisfiedLinkError e) {
            System.out.println(line);
        }
    }
}
