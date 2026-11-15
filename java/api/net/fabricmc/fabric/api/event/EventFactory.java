package net.fabricmc.fabric.api.event;

import com.modhost.api.Bootstrap0;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.function.Function;

public final class EventFactory {
    private EventFactory() {}

    public static <T> Event<T> createArrayBacked(Class<? super T> type, Function<T[], T> invokerFactory) {
        return new ArrayBacked<T>(type, invokerFactory);
    }

    private static final class ArrayBacked<T> extends Event<T> {
        private final Class<? super T> type;
        private final Function<T[], T> factory;
        private T[] handlers;

        @SuppressWarnings("unchecked")
        ArrayBacked(Class<? super T> type, Function<T[], T> factory) {
            this.type = type;
            this.factory = factory;
            this.handlers = (T[]) Array.newInstance(type, 0);
            this.invoker = factory.apply(handlers);
        }

        @SuppressWarnings("unchecked")
        public synchronized void register(T listener) {
            T gated = listener;
            if (type.isInterface()) {
                // skip the listener while the mod that registered it is disabled; isolate its failures
                gated = (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                        new Gate(Bootstrap0.currentOwner(), listener));
            }
            handlers = Arrays.copyOf(handlers, handlers.length + 1);
            handlers[handlers.length - 1] = gated;
            invoker = factory.apply(handlers);
        }
    }

    private static final class Gate implements InvocationHandler {
        private final String owner;
        private final Object target;
        Gate(String owner, Object target) { this.owner = owner; this.target = target; }

        public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            if (m.getDeclaringClass() == Object.class) return m.invoke(target, args);
            if (!Bootstrap0.isActive(owner)) return empty(m.getReturnType());
            try {
                return m.invoke(target, args);
            } catch (InvocationTargetException e) {
                Bootstrap0.failOwner(owner, e.getCause() == null ? e : e.getCause());
                return empty(m.getReturnType());
            }
        }

        private static Object empty(Class<?> rt) {
            if (rt == void.class || !rt.isPrimitive()) return null;
            return Array.get(Array.newInstance(rt, 1), 0);
        }
    }
}
