package com.modhost.bedrock;

import com.modhost.api.EventHandler;
import com.modhost.api.Events;
import com.modhost.api.NativeBridge;

/**
 * Native hooks into Bedrock's own code. The C++ side finds each function in libminecraftpe.so using the
 * patterns in <dataDir>/signatures/<name>.txt and raises the event when the game runs it. A hook that was not
 * resolved simply never fires: check {@link #resolved(String)}.
 *
 * Events with a located function in the shipped build: world.join, world.leave, player.login (server side).
 * Also "hook.first" (data = event name) the first time each hook runs. See docs/HOOKS.md for what is not hooked yet.
 */
public final class Hooks {
    static final Hooks INSTANCE = new Hooks();

    private Hooks() {}

    public void on(String event, EventHandler handler) { Events.on(event, handler); }

    /** True if the native side located and installed the hook behind this event. */
    public boolean resolved(String event) {
        try { return NativeBridge.hookStatus().contains("\n" + event + "=ok"); }
        catch (Throwable t) { return false; }
    }

    /** One line per hook: "name=ok" or "name=missing". */
    public String report() {
        try { return NativeBridge.hookStatus(); } catch (Throwable t) { return "native hooks unavailable"; }
    }
}
