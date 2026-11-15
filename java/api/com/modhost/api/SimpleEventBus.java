package com.modhost.api;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** NeoForge-style event bus. Handlers of disabled mods are skipped; a handler that throws disables its mod. */
public final class SimpleEventBus implements IEventBus {
    private static final class Reg {
        final String owner;
        final Class<?> type; // null = untyped
        final EventPriority priority;
        final Consumer<Object> fn;
        final Object source;
        Reg(String owner, Class<?> type, EventPriority priority, Consumer<Object> fn, Object source) {
            this.owner = owner; this.type = type; this.priority = priority; this.fn = fn; this.source = source;
        }
    }

    private final List<Reg> regs = new CopyOnWriteArrayList<Reg>();

    public void register(Object target) {
        final boolean isClass = target instanceof Class;
        Class<?> c = isClass ? (Class<?>) target : target.getClass();
        for (final Method m : c.getDeclaredMethods()) {
            SubscribeEvent s = m.getAnnotation(SubscribeEvent.class);
            if (s == null || Modifier.isStatic(m.getModifiers()) != isClass) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 1 || !Event.class.isAssignableFrom(p[0])) continue;
            m.setAccessible(true);
            final Object receiver = isClass ? null : target;
            add(p[0], s.priority(), new Consumer<Object>() {
                public void accept(Object event) {
                    try {
                        m.invoke(receiver, event);
                    } catch (InvocationTargetException e) {
                        rethrow(e.getCause() == null ? e : e.getCause());
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }, target);
        }
    }

    public void unregister(Object target) {
        for (Reg r : regs) if (r.source == target) regs.remove(r);
    }

    public <T extends Event> void addListener(Class<T> type, Consumer<T> listener) {
        addListener(EventPriority.NORMAL, type, listener);
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> void addListener(EventPriority priority, Class<T> type, Consumer<T> listener) {
        add(type, priority, (Consumer<Object>) (Consumer<?>) listener, listener);
    }

    public <T extends Event> void addListener(Consumer<T> listener) {
        addListener(EventPriority.NORMAL, listener);
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> void addListener(EventPriority priority, Consumer<T> listener) {
        add(null, priority, (Consumer<Object>) (Consumer<?>) listener, listener);
    }

    public <T extends Event> T post(T event) {
        for (Reg r : regs) {
            if (r.type != null && !r.type.isInstance(event)) continue;
            if (!Bootstrap.isActive(r.owner)) continue;
            try {
                r.fn.accept(event);
            } catch (ClassCastException e) {
                if (r.type != null) Bootstrap.failOwner(r.owner, e); // untyped listeners just ignore other event types
            } catch (Throwable t) {
                Bootstrap.failOwner(r.owner, t);
            }
        }
        return event;
    }

    private void add(Class<?> type, EventPriority p, Consumer<Object> fn, Object source) {
        regs.add(new Reg(Events.owner, type, p, fn, source));
        regs.sort(new Comparator<Reg>() {
            public int compare(Reg a, Reg b) { return a.priority.ordinal() - b.priority.ordinal(); }
        });
    }

    private static void rethrow(Throwable t) {
        if (t instanceof RuntimeException) throw (RuntimeException) t;
        if (t instanceof Error) throw (Error) t;
        throw new RuntimeException(t);
    }
}
