package com.modhost.api;

/** Run code on the Android UI thread, now or after a delay measured in ticks (1 tick = 50 ms). */
public final class Scheduler {
    private Scheduler() {}
    public static void runOnMain(Runnable r) { Platform.post(r, 0); }
    public static void runLater(Runnable r, int ticks) { Platform.post(r, ticks * 50L); }
}
