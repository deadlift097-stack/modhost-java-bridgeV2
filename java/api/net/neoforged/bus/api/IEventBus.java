package net.neoforged.bus.api;

import java.util.function.Consumer;

public interface IEventBus {
    /** Registers @SubscribeEvent methods of an instance, or the static ones when given a Class. */
    void register(Object target);
    void unregister(Object target);
    <T extends Event> T post(T event);

    <T extends Event> void addListener(Class<T> type, Consumer<T> listener);
    <T extends Event> void addListener(EventPriority priority, Class<T> type, Consumer<T> listener);

    /** Type is not inferred here: the listener sees every event and ignores those it cannot cast. */
    <T extends Event> void addListener(Consumer<T> listener);
    <T extends Event> void addListener(EventPriority priority, Consumer<T> listener);
}
