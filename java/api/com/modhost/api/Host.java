package com.modhost.api;

import android.app.Activity;

/** Public window onto the Android side for the Bedrock API (the menu owns the current Activity). */
public final class Host {
    private Host() {}

    /** The game's Activity, or null before it has resumed. */
    public static Activity activity() { return MenuUi.activity(); }

    public static void ui(Runnable r) { Platform.post(r, 0); }

    public static String dataRoot() { return Bootstrap.dataRoot(); }
}
