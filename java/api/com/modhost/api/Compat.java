package com.modhost.api;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Feeds Fabric- and NeoForge-style events from the ModHost event sources. */
public final class Compat {
    private static boolean started;

    private Compat() {}

    static void tickStart() {
        try {
            ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(Minecraft.getInstance());
            NeoForge.EVENT_BUS.post(new ClientTickEvent.Pre());
        } catch (Throwable t) {
            Events.reportOnce("compat.tick.start." + t.getClass().getName(), "tick (start): " + Bootstrap.explain(t));
        }
    }

    static void tickEnd() {
        try {
            ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(Minecraft.getInstance());
            NeoForge.EVENT_BUS.post(new ClientTickEvent.Post());
        } catch (Throwable t) {
            Events.reportOnce("compat.tick.end." + t.getClass().getName(), "tick (end): " + Bootstrap.explain(t));
        }
    }

    static void appResumed() {
        if (started) return;
        started = true;
        try {
            ClientLifecycleEvents.CLIENT_STARTED.invoker().onClientStarted(Minecraft.getInstance());
        } catch (Throwable t) {
            Events.reportOnce("compat.started." + t.getClass().getName(), "client started: " + Bootstrap.explain(t));
        }
    }
}
