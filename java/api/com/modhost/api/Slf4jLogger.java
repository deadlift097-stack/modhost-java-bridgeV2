package com.modhost.api;

import org.slf4j.Logger;

/** slf4j Logger that writes to the ModHost log (and so to logcat and the in-game menu). */
public final class Slf4jLogger implements Logger {
    private final String name;
    private final ModLogger out;

    public Slf4jLogger(String name) {
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

    public void trace(String msg) { log(0, msg, null, null); }
    public void trace(String format, Object arg) { log(0, format, new Object[] { arg }, null); }
    public void trace(String format, Object arg1, Object arg2) { log(0, format, new Object[] { arg1, arg2 }, null); }
    public void trace(String format, Object... arguments) { log(0, format, arguments, null); }
    public void trace(String msg, Throwable t) { log(0, msg, null, t); }
    public void debug(String msg) { log(0, msg, null, null); }
    public void debug(String format, Object arg) { log(0, format, new Object[] { arg }, null); }
    public void debug(String format, Object arg1, Object arg2) { log(0, format, new Object[] { arg1, arg2 }, null); }
    public void debug(String format, Object... arguments) { log(0, format, arguments, null); }
    public void debug(String msg, Throwable t) { log(0, msg, null, t); }
    public void info(String msg) { log(1, msg, null, null); }
    public void info(String format, Object arg) { log(1, format, new Object[] { arg }, null); }
    public void info(String format, Object arg1, Object arg2) { log(1, format, new Object[] { arg1, arg2 }, null); }
    public void info(String format, Object... arguments) { log(1, format, arguments, null); }
    public void info(String msg, Throwable t) { log(1, msg, null, t); }
    public void warn(String msg) { log(2, msg, null, null); }
    public void warn(String format, Object arg) { log(2, format, new Object[] { arg }, null); }
    public void warn(String format, Object arg1, Object arg2) { log(2, format, new Object[] { arg1, arg2 }, null); }
    public void warn(String format, Object... arguments) { log(2, format, arguments, null); }
    public void warn(String msg, Throwable t) { log(2, msg, null, t); }
    public void error(String msg) { log(3, msg, null, null); }
    public void error(String format, Object arg) { log(3, format, new Object[] { arg }, null); }
    public void error(String format, Object arg1, Object arg2) { log(3, format, new Object[] { arg1, arg2 }, null); }
    public void error(String format, Object... arguments) { log(3, format, arguments, null); }
    public void error(String msg, Throwable t) { log(3, msg, null, t); }

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
