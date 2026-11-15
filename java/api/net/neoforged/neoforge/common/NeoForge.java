package net.neoforged.neoforge.common;

import com.modhost.api.SimpleEventBus;
import net.neoforged.bus.api.IEventBus;

public final class NeoForge {
    private NeoForge() {}
    /** Game event bus (client tick events and so on). Each mod also gets its own mod bus in its constructor. */
    public static final IEventBus EVENT_BUS = new SimpleEventBus();
}
