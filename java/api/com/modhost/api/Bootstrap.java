package com.modhost.api;

import net.fabricmc.api.ClientModInitializer;
import com.modhost.bedrock.BedrockMod;
import net.fabricmc.api.ModInitializer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entry points called from C++. A mod is <name>.jar (dexed) + <name>.json:
 *   {"id","main"}                 ModHost mod (ModHostMod)
 *   {"id","entrypoints":[...]}    Fabric-style (ModInitializer / ClientModInitializer)
 *   {"id","neoforge":[...]}       NeoForge-style (@Mod classes)
 * Optional "name" and "version" are shown in the menu.
 */
public final class Bootstrap {
    static final int OK = 0, INCOMPATIBLE = 1, ERROR = 2;

    static final class Entry {
        final String id;
        String name, version = "", author = "", entry = "", kind = "ModHost", reason = "";
        boolean library;
        ModHostMod mod;
        ModContext ctx;
        int status = OK;
        boolean enabled; // user preference (persisted)
        boolean active;  // onEnable has run
        Entry(String id) { this.id = id; this.name = id; }
    }

    private static final List<Entry> MODS = new ArrayList<Entry>();
    private static final Set<String> DISABLED = new HashSet<String>();
    private static final ModLogger LOG = new ModLogger("ModHost");
    private static final Pattern MAIN = Pattern.compile("\"main\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern AUTHOR = Pattern.compile("\"author\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern LIBRARY = Pattern.compile("\"library\"\\s*:\\s*true");
    private static final Pattern BEDROCK = Pattern.compile("\"bedrock\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern VERSION = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern ENTRIES = Pattern.compile("\"entrypoints\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern NEO = Pattern.compile("\"neoforge\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern RESOLUTION = Pattern.compile("Failed resolution of: L([^;]+);");
    private static final Pattern NOT_FOUND = Pattern.compile("Didn't find class \"([^\"]+)\"");
    private static final Pattern CNF = Pattern.compile("ClassNotFoundException: ([\\w.$]+)");
    private static final String[] JAVA_EDITION = {
        "net/minecraft/", "com/mojang/", "org/spongepowered/", "net/fabricmc/fabric/", "net/fabricmc/loader/impl/",
        "net/fabricmc/mapping", "net/neoforged/", "net/minecraftforge/", "org/lwjgl/", "io/netty/",
        "it/unimi/", "com/google/common/"
    };
    private static File disabledFile;
    private static String dataRootPath = "";
    private static boolean running;

    private Bootstrap() {}

    // ---------------- called from C++ ----------------

    public static void loadMods(String modsDir, String dataRoot) {
        dataRootPath = dataRoot;
        new File(dataRoot).mkdirs();
        disabledFile = new File(dataRoot, "disabled.txt");
        readDisabled();
        File[] metas = new File(modsDir).listFiles(new FilenameFilter() {
            public boolean accept(File d, String n) { return n.endsWith(".json"); }
        });
        if (metas == null) { LOG.warn("no mods dir: " + modsDir); return; }
        for (File meta : metas) {
            try {
                loadOne(meta, dataRoot);
            } catch (Throwable t) {
                LOG.error("failed to read " + meta.getName() + ": " + explain(t));
            }
        }
        int ok = 0;
        for (Entry e : MODS) if (e.status == OK) ok++;
        LOG.info(ok + " of " + MODS.size() + " Java mod(s) loaded");
    }

    public static void enableAll() {
        running = true;
        for (Entry e : MODS) if (e.enabled && e.status == OK) callEnable(e);
        Platform.start();
    }

    public static void disableAll() {
        for (Entry e : MODS) if (e.active) callDisable(e);
        Platform.stop();
        running = false;
    }

    public static void tick() { Events.fireTick(); }

    /** Native hooks use this to raise game events. */
    public static void fire(String name, String data) { Events.post(name, data); }

    // ---------------- used by the menu / event bus ----------------

    static List<Entry> list() { return MODS; }
    static String dataRoot() { return dataRootPath; }

    static boolean isActive(String owner) {
        if ("core".equals(owner)) return true;
        for (Entry e : MODS) if (e.id.equals(owner)) return e.active;
        return true;
    }

    static void setEnabled(String id, boolean on) {
        for (Entry e : MODS) {
            if (!e.id.equals(id) || e.status != OK) continue;
            e.enabled = on;
            if (on) DISABLED.remove(id); else DISABLED.add(id);
            writeDisabled();
            if (running && on && !e.active) callEnable(e);
            if (!on && e.active) callDisable(e);
        }
    }

    /** A mod's handler or lifecycle method threw: mark the mod, say why once, stop calling it. */
    static void failOwner(String owner, Throwable t) {
        for (Entry e : MODS) {
            if (e.id.equals(owner)) {
                if (e.status == OK) fail(e, t);
                return;
            }
        }
        Events.reportOnce("owner." + owner + "." + t.getClass().getName(), owner + ": " + explain(t));
    }

    // ---------------- internals ----------------

    private static void loadOne(File meta, String dataRoot) throws Exception {
        String json = read(meta);
        Entry e = new Entry(group(ID, json, meta.getName().replace(".json", "")));
        e.name = group(NAME, json, e.id);
        e.version = group(VERSION, json, "");
        e.author = group(AUTHOR, json, "");
        e.library = LIBRARY.matcher(json).find();

        List<String> mains = new ArrayList<String>();
        Matcher mm = MAIN.matcher(json);
        if (mm.find()) mains.add(mm.group(1));
        Matcher bm = BEDROCK.matcher(json);
        if (bm.find()) mains.add(bm.group(1));
        e.entry = mains.isEmpty() ? "" : mains.get(0);
        mains.addAll(quoted(ENTRIES, json));
        List<String> neoClasses = quoted(NEO, json);

        File data = new File(dataRoot, e.id);
        data.mkdirs();
        e.ctx = new ModContext(e.id, data.getAbsolutePath());
        e.enabled = !DISABLED.contains(e.id);
        MODS.add(e);

        Events.owner = e.id;
        try {
            ModHostMod mod = null;
            List<Object> fabric = new ArrayList<Object>();
            List<ModHostMod> neo = new ArrayList<ModHostMod>();
            for (String cn : mains) {
                Object o = Class.forName(cn, true, Bootstrap.class.getClassLoader())
                        .getDeclaredConstructor().newInstance();
                if (o instanceof ModHostMod) {
                    if (mod == null) { mod = (ModHostMod) o; if (o instanceof BedrockMod) e.kind = "Bedrock API"; }
                }
                else if (o instanceof ModInitializer || o instanceof ClientModInitializer) fabric.add(o);
                else LOG.warn(e.id + ": " + cn + " is not a supported entrypoint");
            }
            for (String cn : neoClasses) {
                ModHostMod m = NeoSupport.create(e.id, cn);
                if (m != null) neo.add(m);
            }
            if (mod == null && (!fabric.isEmpty() || !neo.isEmpty())) {
                mod = new Composite(fabric, neo);
                e.kind = !fabric.isEmpty() && !neo.isEmpty() ? "Fabric + NeoForge style"
                        : !fabric.isEmpty() ? "Fabric-style" : "NeoForge-style";
            }
            if (mod == null) {
                e.status = ERROR;
                e.reason = "no supported entrypoint in " + meta.getName();
                LOG.warn(e.id + ": " + e.reason);
                return;
            }
            e.mod = mod;
            mod.onLoad(e.ctx);
            LOG.info("loaded " + e.id + " (" + e.kind + ")");
        } catch (Throwable t) {
            fail(e, t);
        } finally {
            Events.owner = "core";
        }
    }

    private static void callEnable(Entry e) {
        e.active = true;
        Events.owner = e.id;
        try { e.mod.onEnable(); }
        catch (Throwable t) { fail(e, t); }
        finally { Events.owner = "core"; }
    }

    private static void callDisable(Entry e) {
        Events.owner = e.id;
        try { e.mod.onDisable(); }
        catch (Throwable t) { LOG.error(e.id + ": " + explain(t)); }
        finally { Events.owner = "core"; e.active = false; }
    }

    private static void fail(Entry e, Throwable t) {
        String cls = missingClass(t);
        String gap = linkageGap(t);
        if (cls != null && isJavaEdition(cls)) {
            e.status = INCOMPATIBLE;
            e.reason = "Java Edition only (needs " + cls.replace('/', '.') + ")";
        } else if (cls != null) {
            e.status = ERROR;
            e.reason = "missing library " + cls.replace('/', '.');
        } else if (gap != null && gap.contains("net/minecraft")) {
            e.status = INCOMPATIBLE;
            e.reason = "Java Edition only (calls game code: " + gap + ")";
        } else if (gap != null) {
            e.status = ERROR;
            e.reason = "unsupported API call: " + gap;
        } else {
            e.status = ERROR;
            e.reason = rootMessage(t);
        }
        e.active = false;
        LOG.warn(e.id + ": " + e.reason);
    }

    /** For NoSuchMethodError / NoSuchFieldError: a short "what was called" text, else null. */
    private static String linkageGap(Throwable t) {
        for (Throwable x = t; x != null; x = x.getCause() == x ? null : x.getCause()) {
            if (x instanceof IncompatibleClassChangeError) {
                String m = String.valueOf(x.getMessage());
                int cut = m.indexOf(" or its super classes");
                if (cut < 0) cut = m.indexOf(" (declaration of");
                if (cut > 0) m = m.substring(0, cut);
                return m.length() > 150 ? m.substring(0, 150) : m;
            }
        }
        return null;
    }

    private static boolean isJavaEdition(String cls) {
        for (String p : JAVA_EDITION) if (cls.startsWith(p)) return true;
        return false;
    }

    /** Name (slash form) of the class that could not be found, or null if the error is something else. */
    private static String missingClass(Throwable t) {
        for (Throwable x = t; x != null; x = x.getCause() == x ? null : x.getCause()) {
            String s = String.valueOf(x);
            Matcher m = RESOLUTION.matcher(s);
            if (m.find()) return m.group(1);
            m = NOT_FOUND.matcher(s);
            if (m.find()) return m.group(1).replace('.', '/');
            m = CNF.matcher(s);
            if (m.find()) return m.group(1).replace('.', '/');
        }
        return null;
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        return String.valueOf(r);
    }

    static String explain(Throwable t) {
        String cls = missingClass(t);
        if (cls != null && isJavaEdition(cls)) return "Java Edition only (needs " + cls.replace('/', '.') + ")";
        if (cls != null) return "missing library " + cls.replace('/', '.');
        String gap = linkageGap(t);
        if (gap != null) return "unsupported API call: " + gap;
        return rootMessage(t);
    }

    /** Runs Fabric-style initializers and NeoForge-style mods behind one ModHostMod. */
    private static final class Composite implements ModHostMod {
        private final List<Object> inits;
        private final List<ModHostMod> others;
        private boolean done;
        Composite(List<Object> inits, List<ModHostMod> others) { this.inits = inits; this.others = others; }
        public void onLoad(ModContext ctx) { for (ModHostMod m : others) m.onLoad(ctx); }
        public void onEnable() {
            if (done) return;
            done = true;
            for (Object o : inits) {
                if (o instanceof ModInitializer) ((ModInitializer) o).onInitialize();
                if (o instanceof ClientModInitializer) ((ClientModInitializer) o).onInitializeClient();
            }
            for (ModHostMod m : others) m.onEnable();
        }
        public void onDisable() { for (ModHostMod m : others) m.onDisable(); }
    }

    private static String group(Pattern p, String s, String fallback) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : fallback;
    }

    private static List<String> quoted(Pattern arrayPattern, String json) {
        List<String> out = new ArrayList<String>();
        Matcher a = arrayPattern.matcher(json);
        if (a.find()) {
            Matcher q = QUOTED.matcher(a.group(1));
            while (q.find()) out.add(q.group(1));
        }
        return out;
    }

    private static void readDisabled() {
        DISABLED.clear();
        if (disabledFile == null || !disabledFile.exists()) return;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(disabledFile), "UTF-8"));
            try { String l; while ((l = r.readLine()) != null) if (l.trim().length() > 0) DISABLED.add(l.trim()); }
            finally { r.close(); }
        } catch (Throwable t) { LOG.warn("could not read disabled.txt: " + t); }
    }

    private static void writeDisabled() {
        if (disabledFile == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (String s : DISABLED) sb.append(s).append('\n');
            FileOutputStream out = new FileOutputStream(disabledFile);
            try { out.write(sb.toString().getBytes("UTF-8")); } finally { out.close(); }
        } catch (Throwable t) { LOG.warn("could not save disabled.txt: " + t); }
    }

    private static String read(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally { in.close(); }
    }
}
