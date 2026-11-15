package org.apache.logging.log4j;

import com.modhost.api.Log4jLogger;

public final class LogManager {
    private LogManager() {}

    public static Logger getLogger() {
        StackTraceElement[] st = new Throwable().getStackTrace();
        return new Log4jLogger(st.length > 1 ? st[1].getClassName() : "root");
    }
    public static Logger getLogger(Class<?> clazz) { return new Log4jLogger(clazz.getName()); }
    public static Logger getLogger(String name) { return new Log4jLogger(name); }
    public static Logger getRootLogger() { return new Log4jLogger("root"); }
}
