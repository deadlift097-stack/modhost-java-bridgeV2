package com.modhost.api;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

/** log4j Logger that writes to the ModHost log (and so to logcat and the in-game menu). */
public final class Log4jLogger implements Logger {
    private final String name;
    private final ModLogger out;

    public Log4jLogger(String name) {
        this.name = name;
        int dot = name.lastIndexOf('.');
        this.out = new ModLogger(dot >= 0 ? name.substring(dot + 1) : name);
    }

    public String getName() { return name; }
    public boolean isTraceEnabled() { return false; }
    public boolean isDebugEnabled() { return true; }
    public boolean isInfoEnabled() { return true; }
    public boolean isWarnEnabled() { return true; }
    public boolean isErrorEnabled() { return true; }
    public boolean isFatalEnabled() { return true; }

    public void trace(String message) { log(0, message, null, null); }
    public void trace(Object message) { log(0, String.valueOf(message), null, null); }
    public void trace(String message, Object p0) { log(0, message, new Object[] { p0 }, null); }
    public void trace(String message, Object p0, Object p1) { log(0, message, new Object[] { p0, p1 }, null); }
    public void trace(String message, Object... params) { log(0, message, params, null); }
    public void trace(String message, Throwable t) { log(0, message, null, t); }
    public void debug(String message) { log(0, message, null, null); }
    public void debug(Object message) { log(0, String.valueOf(message), null, null); }
    public void debug(String message, Object p0) { log(0, message, new Object[] { p0 }, null); }
    public void debug(String message, Object p0, Object p1) { log(0, message, new Object[] { p0, p1 }, null); }
    public void debug(String message, Object... params) { log(0, message, params, null); }
    public void debug(String message, Throwable t) { log(0, message, null, t); }
    public void info(String message) { log(1, message, null, null); }
    public void info(Object message) { log(1, String.valueOf(message), null, null); }
    public void info(String message, Object p0) { log(1, message, new Object[] { p0 }, null); }
    public void info(String message, Object p0, Object p1) { log(1, message, new Object[] { p0, p1 }, null); }
    public void info(String message, Object... params) { log(1, message, params, null); }
    public void info(String message, Throwable t) { log(1, message, null, t); }
    public void warn(String message) { log(2, message, null, null); }
    public void warn(Object message) { log(2, String.valueOf(message), null, null); }
    public void warn(String message, Object p0) { log(2, message, new Object[] { p0 }, null); }
    public void warn(String message, Object p0, Object p1) { log(2, message, new Object[] { p0, p1 }, null); }
    public void warn(String message, Object... params) { log(2, message, params, null); }
    public void warn(String message, Throwable t) { log(2, message, null, t); }
    public void error(String message) { log(3, message, null, null); }
    public void error(Object message) { log(3, String.valueOf(message), null, null); }
    public void error(String message, Object p0) { log(3, message, new Object[] { p0 }, null); }
    public void error(String message, Object p0, Object p1) { log(3, message, new Object[] { p0, p1 }, null); }
    public void error(String message, Object... params) { log(3, message, params, null); }
    public void error(String message, Throwable t) { log(3, message, null, t); }
    public void fatal(String message) { log(3, message, null, null); }
    public void fatal(Object message) { log(3, String.valueOf(message), null, null); }
    public void fatal(String message, Object p0) { log(3, message, new Object[] { p0 }, null); }
    public void fatal(String message, Object p0, Object p1) { log(3, message, new Object[] { p0, p1 }, null); }
    public void fatal(String message, Object... params) { log(3, message, params, null); }
    public void fatal(String message, Throwable t) { log(3, message, null, t); }

    public void log(Level level, String message) { log(map(level), message, null, null); }
    public void log(Level level, String message, Object... params) { log(map(level), message, params, null); }
    public void log(Level level, String message, Throwable t) { log(map(level), message, null, t); }

    private static int map(Level l) {
        if (l == Level.FATAL || l == Level.ERROR) return 3;
        if (l == Level.WARN) return 2;
        if (l == Level.INFO) return 1;
        return 0;
    }

    private void log(int level, String format, Object[] args, Throwable t) {
        StringBuilder sb = new StringBuilder();
        String f = format == null ? "null" : format;
        int used = 0, from = 0;
        while (true) {
            int i = f.indexOf("{}", from);
            if (i < 0 || args == null || used >= args.length) { sb.append(f, from, f.length()); break; }
            sb.append(f, from, i).append(String.valueOf(args[used++]));
            from = i + 2;
        }
        if (t == null && args != null && args.length > used && args[args.length - 1] instanceof Throwable) {
            t = (Throwable) args[args.length - 1];
        }
        if (t != null) sb.append(" | ").append(t);
        out.log(level, sb.toString());
    }
}
