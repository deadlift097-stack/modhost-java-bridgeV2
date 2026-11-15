package org.slf4j;

import com.modhost.api.Slf4jLogger;

public final class LoggerFactory {
    private LoggerFactory() {}
    public static Logger getLogger(String name) { return new Slf4jLogger(name); }
    public static Logger getLogger(Class<?> clazz) { return new Slf4jLogger(clazz.getName()); }
}
