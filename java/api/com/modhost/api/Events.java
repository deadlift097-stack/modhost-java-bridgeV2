package com.modhost.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * String-named event bus. Built-in events: "tick" (20/s), "app.resumed", "app.paused",
 * "config.changed", "config.file.changed". Mods may invent any event name: Events.post("my.event", "{}").
 * Native hooks call modhost::bridge().fire(name, data). Register handlers in onLoad (not onEnable) so they
 * are not duplicated when a mod is toggled; handlers of disabled mods are skipped automatically.
 * Fabric-style (ClientTickEvents...) and NeoForge-style (NeoForge.EVENT_BUS) events are fed from here too.
 */
public final class Events {
    private static final class Reg {
        final String owner, name;
        final EventHandler handler;
        Reg(String owner, String name, EventHandler handler) {
            this.owner = owner; this.name = name; this.handler = handler;
        }
    }

    private static final List<Reg> REGS = new CopyOnWriteArrayList<Reg>();
    private static final Set<String> REPORTED = new HashSet<String>();
    static volatile String owner = "core"; // set by Bootstrap while a mod lifecycle method runs

    private Events() {}

    public static void on(String name, EventHandler handler) {
        REGS.add(new Reg(owner, name, handler));
    }

    public static void onTick(final Runnable r) {
        on("tick", new EventHandler() {
            public void handle(String n, String d) { r.run(); }
        });
    }

    public static void post(String name, String data) {
        for (Reg r : REGS) {
            if (!r.name.equals(name) || !Bootstrap.isActive(r.owner)) continue;
            try {
                r.handler.handle(name, data == null ? "" : data);
            } catch (Throwable t) {
                Bootstrap.failOwner(r.owner, t);
            }
        }
    }

    static void fireTick() {
        Compat.tickStart();
        post("tick", "");
        Compat.tickEnd();
    }

    /** Logs a message once per key so a broken handler cannot flood the log 20 times a second. */
    static void reportOnce(String key, String message) {
        boolean first;
        synchronized (REPORTED) { first = REPORTED.add(key); }
        if (first) new ModLogger("ModHost").warn(message);
    }
}
