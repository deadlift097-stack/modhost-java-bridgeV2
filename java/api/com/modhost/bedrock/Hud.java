package com.modhost.bedrock;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.modhost.api.Host;

import java.util.Map;
import java.util.TreeMap;

/** Overlay drawn above the game. Lines are keyed so each mod updates its own line. Touch passes through. */
public final class Hud {
    static final Hud INSTANCE = new Hud();

    private final Map<String, String> lines = new TreeMap<String, String>();
    private TextView view;
    private Activity attachedTo;

    private Hud() {}

    /** Show or update a line. Pass null to remove it. Safe to call from any thread. */
    public void line(final String key, final String text) {
        synchronized (lines) {
            if (text == null) lines.remove(key); else lines.put(key, text);
        }
        Host.ui(new Runnable() { public void run() { render(); } });
    }

    public void clear(String key) { line(key, null); }

    public void toast(final String text) {
        Host.ui(new Runnable() {
            public void run() {
                Activity a = Host.activity();
                if (a != null) Toast.makeText(a, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Called when the game Activity changes or ModHost shuts down. */
    public void detach() {
        Host.ui(new Runnable() {
            public void run() {
                if (view != null && view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                view = null; attachedTo = null;
            }
        });
    }

    private void render() {
        Activity a = Host.activity();
        if (a == null) return;
        String text;
        synchronized (lines) {
            StringBuilder sb = new StringBuilder();
            for (String s : lines.values()) { if (sb.length() > 0) sb.append('\n'); sb.append(s); }
            text = sb.toString();
        }
        if (text.length() == 0) { if (view != null) view.setVisibility(View.GONE); return; }
        if (view == null || attachedTo != a || view.getParent() == null) {
            view = new TextView(a);
            view.setTextColor(Color.WHITE);
            view.setTextSize(11);
            view.setBackgroundColor(0x88000000);
            view.setPadding(12, 6, 12, 6);
            view.setClickable(false);
            view.setFocusable(false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
            lp.setMargins(8, 8, 0, 0);
            a.addContentView(view, lp);
            attachedTo = a;
        }
        view.setText(text);
        view.setVisibility(View.VISIBLE);
    }
}
