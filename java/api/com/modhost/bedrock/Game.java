package com.modhost.bedrock;

import android.app.Activity;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Choreographer;

import com.modhost.api.Events;
import com.modhost.api.Host;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/** Game state read from the running process (frame timing) and from the game folder (worlds). */
public final class Game {
    static final Game INSTANCE = new Game();

    private volatile double fps, frameMs;
    private volatile boolean foreground;
    private long lastNanos, windowStart;
    private int frames;
    private boolean running;
    private final Choreographer.FrameCallback cb = new Choreographer.FrameCallback() {
        public void doFrame(long nanos) {
            if (!running) return;
            if (lastNanos != 0) frameMs = (nanos - lastNanos) / 1e6;
            lastNanos = nanos;
            if (windowStart == 0) windowStart = nanos;
            frames++;
            if (nanos - windowStart >= 1000000000L) {
                fps = frames * 1e9 / (nanos - windowStart);
                frames = 0;
                windowStart = nanos;
                Events.post("fps", String.valueOf(Math.round(fps)));
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private Game() {}

    /** Called by ModHost when the bridge is enabled (UI thread). */
    public void start() {
        if (running || Looper.myLooper() == null) return;
        running = true;
        lastNanos = 0; windowStart = 0; frames = 0;
        Choreographer.getInstance().postFrameCallback(cb);
    }

    public void stop() { running = false; }

    public void setForeground(boolean f) { foreground = f; }

    /** Frames per second over the last second (0 until measured). */
    public double fps() { return fps; }

    /** Duration of the last frame in milliseconds. */
    public double frameMs() { return frameMs; }

    public boolean inForeground() { return foreground; }

    public int screenWidth()  { return metrics()[0]; }
    public int screenHeight() { return metrics()[1]; }

    /** Folder that holds options.txt and minecraftWorlds, or null if it cannot be found. */
    public File gameDir() { return Options.findGameDir(); }

    /** Names of the worlds on disk, newest played first. */
    public List<String> worlds() {
        List<String> out = new ArrayList<String>();
        File dir = gameDir();
        if (dir == null) return out;
        File[] ws = new File(dir, "minecraftWorlds").listFiles();
        if (ws == null) return out;
        java.util.Arrays.sort(ws, new java.util.Comparator<File>() {
            public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
        });
        for (File w : ws) {
            File n = new File(w, "levelname.txt");
            String name = w.getName();
            if (n.isFile()) {
                try {
                    BufferedReader r = new BufferedReader(new FileReader(n));
                    try { String l = r.readLine(); if (l != null && l.trim().length() > 0) name = l.trim(); }
                    finally { r.close(); }
                } catch (Throwable ignored) {}
            }
            out.add(name);
        }
        return out;
    }

    private int[] metrics() {
        Activity a = Host.activity();
        if (a == null) return new int[] { 0, 0 };
        DisplayMetrics m = a.getResources().getDisplayMetrics();
        return new int[] { m.widthPixels, m.heightPixels };
    }
}
