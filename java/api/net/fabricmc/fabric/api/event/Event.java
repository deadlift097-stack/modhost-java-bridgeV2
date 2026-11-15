package net.fabricmc.fabric.api.event;

public abstract class Event<T> {
    protected volatile T invoker;

    /** Calls every registered listener. */
    public final T invoker() { return invoker; }

    public abstract void register(T listener);
}
