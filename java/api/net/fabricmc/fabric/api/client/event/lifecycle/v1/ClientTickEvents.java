package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;

public final class ClientTickEvents {
    private ClientTickEvents() {}

    public static final Event<StartTick> START_CLIENT_TICK = EventFactory.createArrayBacked(StartTick.class,
            callbacks -> client -> { for (StartTick c : callbacks) c.onStartTick(client); });

    public static final Event<EndTick> END_CLIENT_TICK = EventFactory.createArrayBacked(EndTick.class,
            callbacks -> client -> { for (EndTick c : callbacks) c.onEndTick(client); });

    public interface StartTick { void onStartTick(Minecraft client); }
    public interface EndTick { void onEndTick(Minecraft client); }
}
