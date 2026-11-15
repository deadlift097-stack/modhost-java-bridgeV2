package dev.example.hello;

import com.modhost.api.EventHandler;
import com.modhost.api.Events;
import com.modhost.api.ModContext;
import com.modhost.bedrock.Bedrock;
import com.modhost.bedrock.BedrockMod;

/** Smallest Bedrock mod: settings, events, HUD and a toast. */
public class HelloMod implements BedrockMod {
    private ModContext ctx;
    private int ticks;

    public void onLoad(ModContext ctx) {
        this.ctx = ctx;
        ctx.logger.info("Hello from Java! data dir: " + ctx.dataDir);
        ctx.config.toggle("logTicks", "Log tick counter", true)
                  .number("logEverySeconds", "Log every (seconds)", 60, 5, 300)
                  .text("greeting", "Greeting text", "Hello!");

        Events.onTick(new Runnable() {
            public void run() {
                if (!HelloMod.this.ctx.config.getBoolean("logTicks")) return;
                if (++ticks >= HelloMod.this.ctx.config.getDouble("logEverySeconds") * 20) {
                    ticks = 0;
                    HelloMod.this.ctx.logger.info(HelloMod.this.ctx.config.getString("greeting") + " (tick log)");
                }
            }
        });
        Events.on("app.resumed", new EventHandler() {
            public void handle(String name, String data) {
                Bedrock.hud().toast(HelloMod.this.ctx.config.getString("greeting"));
            }
        });
        // real game events from the native hooks (fire only if Mods > Copy report shows world.join=ok)
        Events.on("world.join", new EventHandler() {
            public void handle(String name, String data) {
                Bedrock.hud().toast(HelloMod.this.ctx.config.getString("greeting") + " - joined a world");
                HelloMod.this.ctx.logger.info("world.join fired");
            }
        });
        Events.on("world.leave", new EventHandler() {
            public void handle(String name, String data) { HelloMod.this.ctx.logger.info("world.leave fired"); }
        });
        Events.on("config.changed", new EventHandler() {
            public void handle(String name, String data) { HelloMod.this.ctx.logger.info("setting changed: " + data); }
        });
    }

    public void onEnable()  { ctx.logger.info("enabled"); }
    public void onDisable() { ctx.logger.info("disabled"); }
}
