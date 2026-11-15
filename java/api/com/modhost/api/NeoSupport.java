package com.modhost.api;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.lang.reflect.Constructor;

/** Creates @Mod classes like NeoForge does: constructor gets the mod bus, the container and the Dist. */
final class NeoSupport {
    private NeoSupport() {}

    static ModHostMod create(final String id, String className) throws Exception {
        Class<?> c = Class.forName(className, true, Bootstrap.class.getClassLoader());
        Mod ann = c.getAnnotation(Mod.class);
        if (ann != null && ann.dist().length > 0) {
            boolean client = false;
            for (Dist d : ann.dist()) if (d == Dist.CLIENT) client = true;
            if (!client) return null; // server-only entrypoint
        }
        final SimpleEventBus bus = new SimpleEventBus();
        ModContainer container = new ModContainer() {
            public String getModId() { return id; }
        };

        Constructor<?> best = null;
        for (Constructor<?> k : c.getDeclaredConstructors()) {
            if (best == null || k.getParameterTypes().length > best.getParameterTypes().length) best = k;
        }
        if (best == null) throw new IllegalStateException(className + " has no constructor");
        Class<?>[] pt = best.getParameterTypes();
        Object[] args = new Object[pt.length];
        for (int i = 0; i < pt.length; i++) {
            if (pt[i] == IEventBus.class) args[i] = bus;
            else if (pt[i] == ModContainer.class) args[i] = container;
            else if (pt[i] == Dist.class) args[i] = Dist.CLIENT;
        }
        best.setAccessible(true);
        final Object instance = best.newInstance(args);

        return new ModHostMod() {
            private boolean done;
            public void onLoad(ModContext ctx) {}
            public void onEnable() {
                if (done) return;
                done = true;
                bus.post(new FMLCommonSetupEvent());
                bus.post(new FMLClientSetupEvent());
            }
            public void onDisable() {}
            @Override public String toString() { return String.valueOf(instance); }
        };
    }
}
