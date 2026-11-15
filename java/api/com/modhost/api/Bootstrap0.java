package com.modhost.api;

/** Public doorway for the compatibility packages (net.fabricmc.*, net.neoforged.*) into Bootstrap internals. */
public final class Bootstrap0 {
    private Bootstrap0() {}
    public static String currentOwner() { return Events.owner; }
    public static boolean isActive(String owner) { return Bootstrap.isActive(owner); }
    public static void failOwner(String owner, Throwable t) { Bootstrap.failOwner(owner, t); }
}
