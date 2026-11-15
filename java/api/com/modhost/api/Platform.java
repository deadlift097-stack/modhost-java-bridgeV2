package com.modhost.api;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Field;
import java.util.Map;

/** Android-side event sources: 20 Hz tick, activity lifecycle events, and the in-game menu button. */
final class Platform {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ModLogger LOG = new ModLogger("ModHost");
    private static volatile boolean running;
    private static boolean registered;
    private static int tries;
    private static Application app;

    private Platform() {}

    static void start() {
        if (running) return;
        running = true;
        tries = 0;
        MAIN.post(TICK);
        MAIN.post(new Runnable() { public void run() { com.modhost.bedrock.Bedrock.game().start(); } });
        MAIN.post(ATTACH);
    }

    static void stop() {
        running = false;
        MAIN.post(new Runnable() {
            public void run() {
                MenuUi.remove();
                com.modhost.bedrock.Bedrock.game().stop();
                com.modhost.bedrock.Bedrock.hud().detach();
                if (app != null && registered) {
                    app.unregisterActivityLifecycleCallbacks(CALLBACKS);
                    registered = false;
                }
            }
        });
    }

    static void post(Runnable r, long delayMs) {
        if (delayMs <= 0) MAIN.post(r); else MAIN.postDelayed(r, delayMs);
    }

    // Timer on the Android UI thread; it is not Minecraft's internal tick.
    private static final Runnable TICK = new Runnable() {
        public void run() {
            if (!running) return;
            Events.fireTick();
            MAIN.postDelayed(this, 50);
        }
    };

    private static final Runnable ATTACH = new Runnable() {
        public void run() {
            if (!running) return;
            if (!registered) {
                app = currentApplication();
                if (app != null) {
                    app.registerActivityLifecycleCallbacks(CALLBACKS);
                    registered = true;
                    Activity a = findResumedActivity();
                    if (a != null) MenuUi.attach(a);
                    LOG.info("menu and lifecycle events attached");
                }
            }
            if (!registered && ++tries < 120) MAIN.postDelayed(this, 500);
            else if (!registered) LOG.warn("could not reach the Android Application; in-game menu unavailable");
        }
    };

    private static final Application.ActivityLifecycleCallbacks CALLBACKS =
            new Application.ActivityLifecycleCallbacks() {
        public void onActivityResumed(Activity a) {
            MenuUi.attach(a);
            com.modhost.bedrock.Bedrock.game().setForeground(true);
            Events.post("app.resumed", a.getClass().getName());
            Compat.appResumed();
        }
        public void onActivityPaused(Activity a) {
            com.modhost.bedrock.Bedrock.game().setForeground(false);
            Events.post("app.paused", a.getClass().getName());
        }
        public void onActivityCreated(Activity a, Bundle b) {}
        public void onActivityStarted(Activity a) {}
        public void onActivityStopped(Activity a) {}
        public void onActivitySaveInstanceState(Activity a, Bundle b) {}
        public void onActivityDestroyed(Activity a) {}
    };

    private static Application currentApplication() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            return (Application) at.getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    // Fallback for when the game screen is already resumed before callbacks were registered.
    private static Activity findResumedActivity() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            Field f = at.getDeclaredField("mActivities");
            f.setAccessible(true);
            Map<?, ?> map = (Map<?, ?>) f.get(thread);
            for (Object rec : map.values()) {
                Field paused = rec.getClass().getDeclaredField("paused");
                paused.setAccessible(true);
                if (paused.getBoolean(rec)) continue;
                Field act = rec.getClass().getDeclaredField("activity");
                act.setAccessible(true);
                return (Activity) act.get(rec);
            }
        } catch (Throwable ignored) {
            // hidden API may be blocked on this device; the lifecycle callback will still attach later
        }
        return null;
    }
}
