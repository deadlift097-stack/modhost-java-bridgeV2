package com.modhost.api;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Draggable "Mods" button and a two-pane mod manager drawn over the game: search + sortable list on the left,
 * details / config / log on the right. Same layout and dark theme as the LeviLauncher Mods screen.
 */
final class MenuUi {
    private static final String[] TEXT_EXT = { ".json", ".json5", ".properties", ".toml", ".txt", ".cfg", ".conf", ".yml", ".yaml" };
    private static final String[] SORTS = { "Name A-Z", "Name Z-A", "Enabled first" };

    // theme (matches the LeviLauncher Mods screen)
    private static final int BG = 0xFF262626, BAR = 0xFF333333, CARD = 0xFF2E2E2E, CARD_SEL = 0xFF444444,
            LINE = 0xFF4A4A4A, TEXT = 0xFFEEEEEE, DIM = 0xFFAAAAAA, GREEN = 0xFF3F8F2A, RED = 0xFFB33A3A,
            TEAL = 0xFF1F8A8A, BLUE = 0xFF3B6FD0, ORANGE = 0xFFC9822B, GREY = 0xFF555555;
    private static final int PAGE_DETAILS = 0, PAGE_CONFIG = 1, PAGE_FILE = 2, PAGE_LOG = 3;

    private static Activity host;
    private static TextView button;
    private static FrameLayout overlay;
    private static LinearLayout listCol, detail;
    private static TextView countView, sortBtn, logView;
    private static String selected = "", query = "";
    private static boolean showLibs;
    private static int sort, page;
    private static File openFile;
    private static int btnX = -1, btnY = -1; // px from top-left; -1 = default

    private MenuUi() {}

    static Activity activity() { return host; }

    // ---------------- attach / remove ----------------

    static void attach(final Activity a) {
        a.runOnUiThread(new Runnable() {
            public void run() {
                if (host == a && button != null && button.getParent() != null) return;
                remove();
                host = a;
                loadPos();
                buildButton(a);
                overlay = buildOverlay(a);
                overlay.setVisibility(View.GONE);
                a.addContentView(overlay, new FrameLayout.LayoutParams(-1, -1));
            }
        });
    }

    static void remove() {
        View[] views = { button, overlay };
        for (View v : views) if (v != null && v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        button = null; overlay = null; listCol = null; detail = null; countView = null; sortBtn = null; logView = null; host = null;
    }

    private static void toggle() {
        if (overlay == null) return;
        if (overlay.getVisibility() == View.VISIBLE) overlay.setVisibility(View.GONE);
        else { page = PAGE_DETAILS; refreshAll(); overlay.setVisibility(View.VISIBLE); }
    }

    // ---------------- draggable button ----------------

    private static void buildButton(final Activity a) {
        button = new TextView(a);
        button.setText("Mods");
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackground(box(0xE0182028, 0xFF4A5A6A, 2));
        button.setPadding(dp(a, 14), dp(a, 8), dp(a, 14), dp(a, 8));
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        a.addContentView(button, lp);
        button.post(new Runnable() { public void run() { place(lp, btnX, btnY); } });

        final int slop = ViewConfiguration.get(a).getScaledTouchSlop();
        button.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY, startX, startY;
            boolean dragging;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY();
                        startX = lp.leftMargin; startY = lp.topMargin;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!dragging && Math.abs(dx) + Math.abs(dy) > slop * 2) dragging = true;
                        if (dragging) place(lp, (int) (startX + dx), (int) (startY + dy));
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragging) savePos(lp.leftMargin, lp.topMargin); else toggle();
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    /** Move the button, keeping it fully on screen. x or y of -1 means the default top-right spot. */
    private static void place(FrameLayout.LayoutParams lp, int x, int y) {
        if (button == null || host == null) return;
        int sw = host.getResources().getDisplayMetrics().widthPixels, sh = host.getResources().getDisplayMetrics().heightPixels;
        int w = Math.max(button.getWidth(), dp(host, 60)), h = Math.max(button.getHeight(), dp(host, 36));
        if (x < 0 || y < 0) { x = sw - w - dp(host, 8); y = dp(host, 48); }
        lp.leftMargin = Math.max(0, Math.min(x, sw - w));
        lp.topMargin = Math.max(0, Math.min(y, sh - h));
        button.setLayoutParams(lp);
    }

    private static File posFile() { return new File(Bootstrap.dataRoot(), "ui.properties"); }

    private static void loadPos() {
        btnX = -1; btnY = -1;
        try {
            Properties p = new Properties();
            FileInputStream in = new FileInputStream(posFile());
            try { p.load(in); } finally { in.close(); }
            btnX = Integer.parseInt(p.getProperty("buttonX", "-1"));
            btnY = Integer.parseInt(p.getProperty("buttonY", "-1"));
        } catch (Throwable ignored) {}
    }

    private static void savePos(int x, int y) {
        btnX = x; btnY = y;
        try {
            Properties p = new Properties();
            p.setProperty("buttonX", String.valueOf(x));
            p.setProperty("buttonY", String.valueOf(y));
            FileOutputStream out = new FileOutputStream(posFile());
            try { p.store(out, "Mods button position (drag the button to move it)"); } finally { out.close(); }
        } catch (Throwable t) { new ModLogger("ModHost").warn("could not save button position: " + t); }
    }

    // ---------------- main panel ----------------

    private static FrameLayout buildOverlay(final Activity a) {
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(0x88000000);
        root.setClickable(true); // swallow touches so the game does not move while the panel is open

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(box(BG, LINE, 2));
        int sw = a.getResources().getDisplayMetrics().widthPixels, sh = a.getResources().getDisplayMetrics().heightPixels;
        root.addView(panel, new FrameLayout.LayoutParams((int) (sw * 0.94f), (int) (sh * 0.92f), Gravity.CENTER));

        // header
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(BAR);
        head.setPadding(dp(a, 16), dp(a, 8), dp(a, 8), dp(a, 8));
        TextView title = text(a, "Mods", TEXT, 20);
        head.addView(title);
        countView = text(a, "", DIM, 13);
        countView.setPadding(dp(a, 12), dp(a, 4), 0, 0);
        head.addView(countView, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(button(a, "X", GREY, new View.OnClickListener() {
            public void onClick(View v) { toggle(); }
        }), new LinearLayout.LayoutParams(dp(a, 44), dp(a, 40)));
        panel.addView(head);

        // body: list | detail
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setPadding(dp(a, 10), dp(a, 10), dp(a, 10), dp(a, 6));
        panel.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        body.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));

        EditText search = new EditText(a);
        search.setHint("Search mods...");
        search.setHintTextColor(DIM);
        search.setTextColor(TEXT);
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setBackground(box(0xFF1C1C1C, LINE, 2));
        search.setPadding(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 8));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int i, int c, int n) {}
            public void onTextChanged(CharSequence s, int i, int b, int c) {}
            public void afterTextChanged(Editable s) { query = s.toString().trim().toLowerCase(Locale.US); fillList(); }
        });
        left.addView(search, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout tools = new LinearLayout(a);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(0, dp(a, 6), 0, dp(a, 6));
        CheckBox libs = new CheckBox(a);
        libs.setText("Show libraries");
        libs.setTextColor(DIM);
        libs.setTextSize(13);
        libs.setChecked(showLibs);
        libs.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { showLibs = on; fillList(); }
        });
        tools.addView(libs, new LinearLayout.LayoutParams(0, -2, 1f));
        sortBtn = button(a, SORTS[sort] + "  \u25BE", GREY, new View.OnClickListener() {
            public void onClick(View v) { sort = (sort + 1) % SORTS.length; ((TextView) v).setText(SORTS[sort] + "  \u25BE"); fillList(); }
        });
        sortBtn.setTextSize(12);
        tools.addView(sortBtn, new LinearLayout.LayoutParams(-2, dp(a, 36)));
        left.addView(tools);

        ScrollView ls = new ScrollView(a);
        listCol = new LinearLayout(a);
        listCol.setOrientation(LinearLayout.VERTICAL);
        ls.addView(listCol);
        left.addView(ls, new LinearLayout.LayoutParams(-1, 0, 1f));

        ScrollView rs = new ScrollView(a);
        rs.setBackground(box(0xFF232323, LINE, 2));
        detail = new LinearLayout(a);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(dp(a, 14), dp(a, 14), dp(a, 14), dp(a, 14));
        rs.addView(detail);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1.5f);
        rp.leftMargin = dp(a, 10);
        body.addView(rs, rp);

        // footer
        LinearLayout foot = new LinearLayout(a);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setBackgroundColor(BAR);
        foot.setPadding(dp(a, 16), dp(a, 6), dp(a, 10), dp(a, 6));
        TextView hint = text(a, "Drag the Mods button to move it  \u2022  tap X to close", DIM, 12);
        foot.addView(hint, new LinearLayout.LayoutParams(0, -2, 1f));
        foot.addView(button(a, "Copy report", GREY, new View.OnClickListener() {
            public void onClick(View v) { copyReport(a); }
        }), new LinearLayout.LayoutParams(-2, dp(a, 38)));
        foot.addView(button(a, "Log", GREY, new View.OnClickListener() {
            public void onClick(View v) { page = PAGE_LOG; fillDetail(); }
        }), margin(a, new LinearLayout.LayoutParams(-2, dp(a, 38))));
        foot.addView(button(a, "Refresh", GREY, new View.OnClickListener() {
            public void onClick(View v) { refreshAll(); }
        }), margin(a, new LinearLayout.LayoutParams(-2, dp(a, 38))));
        panel.addView(foot);
        return root;
    }

    private static void refreshAll() { fillList(); fillDetail(); }

    // ---------------- left list ----------------

    private static void fillList() {
        if (listCol == null || host == null) return;
        final Activity a = host;
        listCol.removeAllViews();
        List<Bootstrap.Entry> all = Bootstrap.list();
        List<Bootstrap.Entry> shown = new ArrayList<Bootstrap.Entry>();
        int running = 0;
        for (Bootstrap.Entry e : all) {
            if (e.active) running++;
            if (e.library && !showLibs) continue;
            if (query.length() > 0 && !e.name.toLowerCase(Locale.US).contains(query) && !e.id.toLowerCase(Locale.US).contains(query)) continue;
            shown.add(e);
        }
        Collections.sort(shown, new Comparator<Bootstrap.Entry>() {
            public int compare(Bootstrap.Entry x, Bootstrap.Entry y) {
                if (sort == 2 && x.active != y.active) return x.active ? -1 : 1;
                int c = x.name.compareToIgnoreCase(y.name);
                return sort == 1 ? -c : c;
            }
        });
        countView.setText(all.size() + " mods, " + running + " loaded");
        if (shown.isEmpty()) listCol.addView(text(a, all.isEmpty() ? "No Java mods found" : "No match", DIM, 13));
        boolean selectedShown = false;
        for (final Bootstrap.Entry e : shown) {
            if (e.id.equals(selected)) selectedShown = true;
            listCol.addView(card(a, e));
        }
        if (!selectedShown && !shown.isEmpty()) selected = shown.get(0).id;
    }

    private static View card(final Activity a, final Bootstrap.Entry e) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(box(e.id.equals(selected) ? CARD_SEL : CARD, e.id.equals(selected) ? 0xFFFFFFFF : LINE, 2));
        row.setPadding(dp(a, 8), dp(a, 8), dp(a, 8), dp(a, 8));
        row.addView(icon(a, e, 44), new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(a, 10), 0, 0, 0);
        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(text(a, e.name, TEXT, 14));
        TextView ver = text(a, e.version.length() > 0 ? "  " + e.version : "", DIM, 11);
        top.addView(ver);
        col.addView(top);
        LinearLayout badges = new LinearLayout(a);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setPadding(0, dp(a, 4), 0, 0);
        addBadges(a, badges, e);
        col.addView(badges);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(a, 6);
        row.setLayoutParams(lp);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { selected = e.id; page = PAGE_DETAILS; refreshAll(); }
        });
        return row;
    }

    private static void addBadges(Activity a, LinearLayout box, Bootstrap.Entry e) {
        if (e.status == Bootstrap.INCOMPATIBLE) box.addView(badge(a, "Java only", ORANGE));
        else if (e.status == Bootstrap.ERROR) box.addView(badge(a, "Error", RED));
        else if (!e.enabled) box.addView(badge(a, "Disabled", RED));
        box.addView(badge(a, e.kind, e.kind.startsWith("Bedrock") ? TEAL : e.kind.startsWith("Fabric") ? BLUE : GREY));
    }

    // ---------------- right pane ----------------

    private static Bootstrap.Entry current() {
        for (Bootstrap.Entry e : Bootstrap.list()) if (e.id.equals(selected)) return e;
        return null;
    }

    private static void fillDetail() {
        if (detail == null || host == null) return;
        Activity a = host;
        detail.removeAllViews();
        logView = null;
        if (page == PAGE_LOG) { showLog(a); return; }
        Bootstrap.Entry e = current();
        if (e == null) { detail.addView(text(a, "Select a mod", DIM, 14)); return; }
        if (page == PAGE_CONFIG) showConfig(a, e);
        else if (page == PAGE_FILE && openFile != null) showFile(a, e, openFile);
        else showDetails(a, e);
    }

    private static void showDetails(final Activity a, final Bootstrap.Entry e) {
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.addView(icon(a, e, 72), new LinearLayout.LayoutParams(dp(a, 72), dp(a, 72)));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(a, 12), 0, 0, 0);
        col.addView(text(a, e.name, TEXT, 22));
        if (e.version.length() > 0) col.addView(text(a, "Version " + e.version, DIM, 13));
        if (e.author.length() > 0) col.addView(text(a, "By " + e.author, DIM, 13));
        LinearLayout badges = new LinearLayout(a);
        badges.setPadding(0, dp(a, 4), 0, 0);
        addBadges(a, badges, e);
        col.addView(badges);
        head.addView(col);
        detail.addView(head);

        LinearLayout btns = new LinearLayout(a);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, dp(a, 14), 0, dp(a, 14));
        btns.addView(button(a, "Config", GREEN, new View.OnClickListener() {
            public void onClick(View v) { page = PAGE_CONFIG; fillDetail(); }
        }), new LinearLayout.LayoutParams(-2, dp(a, 40)));
        if (e.status == Bootstrap.OK) {
            final boolean on = e.enabled;
            btns.addView(button(a, on ? "Disable" : "Enable", on ? RED : GREEN, new View.OnClickListener() {
                public void onClick(View v) { Bootstrap.setEnabled(e.id, !on); refreshAll(); }
            }), margin(a, new LinearLayout.LayoutParams(-2, dp(a, 40))));
        }
        detail.addView(btns);

        String about = KnownMods.note(e.id);
        if (about != null) { detail.addView(text(a, "About", DIM, 14)); TextView t = text(a, about, TEXT, 12); t.setPadding(0, dp(a, 4), 0, dp(a, 12)); detail.addView(t); }

        detail.addView(text(a, "Details", DIM, 14));
        detail.addView(kv(a, "State", statusText(e), statusColor(e)));
        detail.addView(kv(a, "Type", e.kind, TEXT));
        if (e.entry.length() > 0) detail.addView(kv(a, "Entry", e.entry, TEXT));
        detail.addView(kv(a, "Id", e.id, TEXT));
        if (e.ctx != null) detail.addView(kv(a, "Folder", e.ctx.dataDir, TEXT));
    }

    private static void showConfig(final Activity a, final Bootstrap.Entry e) {
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(a, e.name + " \u2022 Config", TEXT, 18), new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(button(a, "Back", GREY, new View.OnClickListener() {
            public void onClick(View v) { page = PAGE_DETAILS; fillDetail(); }
        }), new LinearLayout.LayoutParams(-2, dp(a, 38)));
        detail.addView(head);

        detail.addView(kv(a, "State", statusText(e), statusColor(e)));
        if (e.status == Bootstrap.OK) {
            CheckBox on = new CheckBox(a);
            on.setText("Enabled");
            on.setTextColor(TEXT);
            on.setChecked(e.enabled);
            on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton b, boolean v) { Bootstrap.setEnabled(e.id, v); fillList(); }
            });
            detail.addView(on);
        }
        if (e.ctx != null && !e.ctx.config.options().isEmpty()) {
            detail.addView(section(a, "Settings"));
            addOptions(a, e.ctx.config);
        }
        detail.addView(section(a, "Config files"));
        List<File> files = configFiles(e);
        if (files.isEmpty()) {
            TextView none = text(a, "None yet. Mods create these the first time they save settings.", 0xFF888888, 12);
            detail.addView(none);
        }
        for (final File f : files) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(a, 38));
            lp.topMargin = dp(a, 4);
            TextView b = button(a, f.getName(), GREY, new View.OnClickListener() {
                public void onClick(View v) { openFile = f; page = PAGE_FILE; fillDetail(); }
            });
            detail.addView(b, lp);
        }
        if (e.ctx != null) { TextView p = text(a, "Folder: " + e.ctx.dataDir, 0xFF888888, 10); p.setTextIsSelectable(true); p.setPadding(0, dp(a, 8), 0, 0); detail.addView(p); }
    }

    private static void addOptions(final Activity a, final ModConfig cfg) {
        for (final ModConfig.Option o : cfg.options()) {
            if (o.type == ModConfig.TOGGLE) {
                CheckBox cb = new CheckBox(a);
                cb.setText(o.label);
                cb.setTextColor(TEXT);
                cb.setChecked(cfg.getBoolean(o.key));
                cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton b, boolean on) { cfg.set(o.key, String.valueOf(on)); }
                });
                detail.addView(cb);
            } else if (o.type == ModConfig.NUMBER) {
                final TextView val = text(a, o.label + ": " + fmt(cfg.getDouble(o.key)), TEXT, 13);
                val.setPadding(0, dp(a, 6), 0, 0);
                detail.addView(val);
                SeekBar sb = new SeekBar(a);
                sb.setMax(1000);
                sb.setProgress((int) Math.round((cfg.getDouble(o.key) - o.min) / (o.max - o.min) * 1000));
                sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar s, int p, boolean user) { val.setText(o.label + ": " + fmt(o.min + (o.max - o.min) * p / 1000.0)); }
                    public void onStartTrackingTouch(SeekBar s) {}
                    public void onStopTrackingTouch(SeekBar s) { cfg.set(o.key, String.valueOf(o.min + (o.max - o.min) * s.getProgress() / 1000.0)); }
                });
                detail.addView(sb);
            } else {
                TextView l = text(a, o.label, TEXT, 13);
                l.setPadding(0, dp(a, 6), 0, 0);
                detail.addView(l);
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                final EditText et = new EditText(a);
                et.setText(cfg.getString(o.key));
                et.setTextColor(TEXT);
                et.setSingleLine(true);
                row.addView(et, new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(button(a, "Set", GREEN, new View.OnClickListener() {
                    public void onClick(View v) { cfg.set(o.key, et.getText().toString()); }
                }), new LinearLayout.LayoutParams(-2, dp(a, 38)));
                detail.addView(row);
            }
        }
    }

    private static void showFile(final Activity a, final Bootstrap.Entry e, final File f) {
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(a, f.getName(), TEXT, 18), new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(button(a, "Back", GREY, new View.OnClickListener() {
            public void onClick(View v) { page = PAGE_CONFIG; fillDetail(); }
        }), new LinearLayout.LayoutParams(-2, dp(a, 38)));
        detail.addView(head);
        if (f.length() > 65536) { detail.addView(text(a, "File is larger than 64 KB; edit it with a file manager.", ORANGE, 12)); return; }
        final EditText et = new EditText(a);
        et.setText(readText(f));
        et.setTextColor(TEXT);
        et.setTypeface(Typeface.MONOSPACE);
        et.setTextSize(11);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setMinLines(8);
        et.setBackground(box(0xFF1C1C1C, LINE, 2));
        detail.addView(et);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(a, 40));
        lp.topMargin = dp(a, 8);
        detail.addView(button(a, "Save", GREEN, new View.OnClickListener() {
            public void onClick(View v) {
                boolean ok = writeText(f, et.getText().toString());
                new ModLogger("ModHost").info((ok ? "saved " : "could not save ") + f.getName());
                if (ok) Events.post("config.file.changed", f.getAbsolutePath());
            }
        }), lp);
    }

    private static void showLog(final Activity a) {
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(a, "Log", TEXT, 18), new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(button(a, "Back", GREY, new View.OnClickListener() {
            public void onClick(View v) { page = PAGE_DETAILS; fillDetail(); }
        }), new LinearLayout.LayoutParams(-2, dp(a, 38)));
        detail.addView(head);
        logView = text(a, "", 0xFFCCCCCC, 11);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        StringBuilder sb = new StringBuilder();
        for (String l : ModLogger.recent(80)) sb.append(l).append('\n');
        logView.setText(sb.toString());
        detail.addView(logView);
    }

    // ---------------- helpers ----------------

    private static List<File> configFiles(Bootstrap.Entry e) {
        List<File> out = new ArrayList<File>();
        if (e.ctx != null) collect(new File(e.ctx.dataDir), null, out);
        collect(new File(Bootstrap.dataRoot(), "config"), e.id.toLowerCase(Locale.US), out);
        return out;
    }

    private static void collect(File dir, String nameMustContain, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        for (File f : fs) {
            if (!f.isFile()) continue;
            String n = f.getName().toLowerCase(Locale.US);
            if (nameMustContain != null && !n.contains(nameMustContain)) continue;
            for (String ext : TEXT_EXT) if (n.endsWith(ext)) { out.add(f); break; }
        }
    }

    private static String readText(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString("UTF-8");
            } finally { in.close(); }
        } catch (Throwable t) { return ""; }
    }

    private static boolean writeText(File f, String text) {
        try {
            FileOutputStream out = new FileOutputStream(f);
            try { out.write(text.getBytes("UTF-8")); } finally { out.close(); }
            return true;
        } catch (Throwable t) { return false; }
    }

    private static String buildReport() {
        StringBuilder sb = new StringBuilder("ModHost report\n");
        for (Bootstrap.Entry e : Bootstrap.list()) {
            sb.append(e.id).append("  ").append(e.name).append(' ').append(e.version)
              .append("  [").append(e.kind).append("]  ").append(statusText(e))
              .append(e.enabled ? "  (enabled)" : "  (disabled)").append('\n');
        }
        sb.append("\nNative hooks:").append(com.modhost.bedrock.Bedrock.hooks().report()).append('\n');
        sb.append("\nLog:\n");
        for (String l : ModLogger.recent(80)) sb.append(l).append('\n');
        return sb.toString();
    }

    private static void copyReport(Activity a) {
        String text = buildReport();
        File f = new File(Bootstrap.dataRoot(), "modhost-report.txt");
        boolean saved = writeText(f, text);
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("ModHost report", text));
            new ModLogger("ModHost").info("report copied to clipboard" + (saved ? " and saved to " + f : ""));
        } catch (Throwable t) {
            new ModLogger("ModHost").warn("clipboard unavailable" + (saved ? "; report saved to " + f : ""));
        }
    }

    private static String statusText(Bootstrap.Entry e) {
        if (e.status == Bootstrap.OK) return e.active ? "Running" : "Loaded, off";
        return e.reason;
    }

    private static int statusColor(Bootstrap.Entry e) {
        if (e.status == Bootstrap.OK) return 0xFF77DD77;
        return e.status == Bootstrap.INCOMPATIBLE ? 0xFFFFAA33 : 0xFFFF6666;
    }

    private static View kv(Activity a, String k, String v, int color) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(a, 4), 0, dp(a, 4));
        TextView kt = text(a, k, DIM, 13);
        row.addView(kt, new LinearLayout.LayoutParams(dp(a, 64), -2));
        TextView vt = text(a, v, color, 13);
        vt.setTextIsSelectable(true);
        row.addView(vt, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private static TextView section(Activity a, String s) {
        TextView t = text(a, s, DIM, 14);
        t.setPadding(0, dp(a, 14), 0, dp(a, 2));
        return t;
    }

    private static TextView icon(Activity a, Bootstrap.Entry e, int sizeDp) {
        TextView t = new TextView(a);
        String n = e.name.length() > 0 ? e.name.substring(0, 1).toUpperCase(Locale.US) : "?";
        t.setText(n);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.45f);
        float[] hsv = { Math.abs(e.id.hashCode()) % 360, 0.55f, 0.7f };
        t.setBackground(box(Color.HSVToColor(hsv), 0x33FFFFFF, 2));
        return t;
    }

    private static TextView badge(Activity a, String s, int color) {
        TextView t = text(a, s, Color.WHITE, 11);
        t.setBackground(box(color, 0x33FFFFFF, 1));
        t.setPadding(dp(a, 6), dp(a, 1), dp(a, 6), dp(a, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(a, 4);
        t.setLayoutParams(lp);
        return t;
    }

    private static TextView button(Activity a, String s, int color, View.OnClickListener l) {
        TextView t = text(a, s, Color.WHITE, 14);
        t.setGravity(Gravity.CENTER);
        t.setBackground(box(color, 0x66000000, 2));
        t.setPadding(dp(a, 14), dp(a, 6), dp(a, 14), dp(a, 6));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    private static LinearLayout.LayoutParams margin(Activity a, LinearLayout.LayoutParams lp) {
        lp.leftMargin = dp(a, 8);
        return lp;
    }

    private static TextView text(Activity a, String s, int color, float sp) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        return t;
    }

    private static GradientDrawable box(int fill, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(Math.max(1, (int) (strokeDp * (host != null ? host.getResources().getDisplayMetrics().density : 2))), stroke);
        return g;
    }

    private static String fmt(double v) { return String.format(Locale.US, "%.1f", v); }

    private static int dp(Activity a, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics());
    }
}
