package net.minecraft.client;

import com.modhost.api.Scheduler;

/**
 * Stand-in for the Java Edition client class so mods can load and register handlers.
 * It carries no game state: Bedrock has no such object. Add members here as native hooks provide data.
 */
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();

    protected Minecraft() {}

    public static Minecraft getInstance() { return INSTANCE; }

    /** Runs on the Android UI thread. */
    public void execute(Runnable r) { Scheduler.runOnMain(r); }
}
