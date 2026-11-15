package dev.modhost.lithium;

import com.modhost.api.EventHandler;
import com.modhost.api.Events;
import com.modhost.api.ModContext;
import com.modhost.bedrock.Bedrock;
import com.modhost.bedrock.BedrockMod;
import com.modhost.bedrock.Options;

/**
 * Lithium rewritten for Bedrock. On Java Edition Lithium makes the game logic cheaper so the game keeps its
 * frame rate. Bedrock's logic is native code a Java mod cannot patch, so this version does the same job with
 * the levers Bedrock exposes: it measures the real frame rate and lowers the view distance in options.txt when
 * the game runs below the target, then raises it back when there is headroom. Bedrock reads options.txt at
 * start-up, so a change applies on the next launch (the HUD says so). Only options that exist are touched.
 */
public class LithiumBedrock implements BedrockMod {
    private static final String KEY = "gfx_viewdistance";
    private ModContext ctx;
    private double sum;
    private int samples, lowSeconds, highSeconds;

    public void onLoad(ModContext c) {
        ctx = c;
        c.config.toggle("autoTune", "Adjust view distance from frame rate", true)
                .number("targetFps", "Target frame rate", 45, 20, 120)
                .number("minDistance", "Lowest view distance", 6, 4, 32)
                .number("maxDistance", "Highest view distance", 12, 6, 64)
                .toggle("showHud", "Show status on screen", true);

        Events.on("fps", new EventHandler() {
            public void handle(String name, String data) { onSecond(); }
        });
    }

    private void onSecond() {
        if (!Bedrock.game().inForeground()) return;
        double fps = Bedrock.game().fps();
        if (fps <= 0) return;
        sum += fps; samples++;
        double target = ctx.config.getDouble("targetFps");
        if (fps < target) { lowSeconds++; highSeconds = 0; }
        else if (fps > target * 1.4) { highSeconds++; lowSeconds = 0; }
        else { lowSeconds = 0; highSeconds = 0; }

        Options o = Bedrock.options();
        String msg = "Lithium: " + Math.round(fps) + " fps";
        if (ctx.config.getBoolean("autoTune") && o.has(KEY)) {
            int cur = o.getInt(KEY, 0);
            int min = (int) ctx.config.getDouble("minDistance"), max = (int) ctx.config.getDouble("maxDistance");
            if (lowSeconds >= 10 && cur > min) { apply(cur - 2 < min ? min : cur - 2, "lowered"); lowSeconds = 0; }
            else if (highSeconds >= 30 && cur < max) { apply(cur + 2 > max ? max : cur + 2, "raised"); highSeconds = 0; }
            msg += "  view distance " + o.getInt(KEY, cur);
        } else if (!o.available()) {
            msg += "  (options.txt not found)";
        }
        if (ctx.config.getBoolean("showHud")) Bedrock.hud().line("lithium", msg);
        else Bedrock.hud().clear("lithium");
    }

    private void apply(int value, String what) {
        if (Bedrock.options().set(KEY, String.valueOf(value))) {
            ctx.logger.info(what + " view distance to " + value + " (applies on next launch)");
            Bedrock.hud().toast("Lithium " + what + " view distance to " + value + " (next launch)");
        }
    }

    public void onEnable()  { ctx.logger.info("enabled"); }
    public void onDisable() { Bedrock.hud().clear("lithium"); ctx.logger.info("disabled"); }
}
