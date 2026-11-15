package net.neoforged.fml.event.lifecycle;

import net.neoforged.bus.api.Event;

public class FMLCommonSetupEvent extends Event {
    /** Runs immediately; there is no separate setup thread here. */
    public void enqueueWork(Runnable r) { r.run(); }
}
