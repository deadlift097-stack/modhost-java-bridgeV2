package dev.modhost.fpshud;

import com.modhost.api.EventHandler;
import com.modhost.api.Events;
import com.modhost.api.ModContext;
import com.modhost.bedrock.Bedrock;
import com.modhost.bedrock.BedrockMod;

/** Example Bedrock mod: frame rate, frame time and Java heap on screen. */
public class FpsHud implements BedrockMod {
    private ModContext ctx;

    public void onLoad(ModContext c) {
        ctx = c;
        c.config.toggle("showFrameTime", "Show frame time", true)
                .toggle("showMemory", "Show Java heap", false);
        Events.on("fps", new EventHandler() {
            public void handle(String name, String data) {
                StringBuilder sb = new StringBuilder(data + " FPS");
                if (ctx.config.getBoolean("showFrameTime")) sb.append("  ").append(Math.round(Bedrock.game().frameMs())).append(" ms");
                if (ctx.config.getBoolean("showMemory")) {
                    Runtime r = Runtime.getRuntime();
                    sb.append("  ").append((r.totalMemory() - r.freeMemory()) >> 20).append(" MB");
                }
                Bedrock.hud().line("fps_hud", sb.toString());
            }
        });
    }

    public void onEnable()  { ctx.logger.info("enabled"); }
    public void onDisable() { Bedrock.hud().clear("fps_hud"); }
}
