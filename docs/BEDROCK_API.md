# Bedrock mod API (`com.modhost.bedrock`)

Instead of translating Java Edition mods, a mod is **rewritten for Bedrock**: you look at what the Java mod
does, then do the same job with what Bedrock actually offers. Java Edition code (`net.minecraft.*`, Mixins)
does not exist here, so it is never loaded.

    {"id":"my_mod","name":"My Mod","author":"you","bedrock":"pkg.MyMod"}

    public class MyMod implements BedrockMod {
        public void onLoad(ModContext ctx) { Events.on("fps", (n, d) -> ...); }
        public void onEnable() {}  public void onDisable() {}
    }

| API | What it does on the real game |
|---|---|
| `Bedrock.game()` | `fps()`, `frameMs()` (measured with Android's Choreographer), `inForeground()`, `screenWidth/Height()`, `gameDir()`, `worlds()` (names read from `minecraftWorlds/*/levelname.txt`) |
| `Bedrock.hud()` | `line(key,text)` text overlay above the game (touch passes through), `toast(text)` |
| `Bedrock.options()` | read/write Bedrock's `options.txt`; `set` only changes keys that exist; applied on next game launch |
| `Bedrock.hooks()` | native hooks into Bedrock's code (below) |
| `Events` | `tick` (20/s), `fps` (1/s), `app.resumed`, `app.paused`, `config.changed`, plus any hook event |
| `ctx.config` | declare settings (toggle / number / text); shown in the mod's Config page |

## Native hooks (reach Bedrock's own code)
Working set, found in the supplied `libminecraftpe.so` (details and limits in docs/HOOKS.md):
`world.join`, `world.leave`, `player.login`. The bridge scans the game for each pattern at start, installs the
detours with the preloader SDK (`pl::memory::hook`), and raises the event from the detour. Mods > Copy report lists
`name=ok` / `name=missing` per hook (`Bedrock.hooks().resolved(event)`).
Not hooked yet: `player.attack`, `chat.send`, `block.break`, `block.place`, `player.tick`, `level.tick`.

## Rewritten mods
* **Lithium** (`java/mods/lithium-bedrock`): Java Lithium makes game logic cheaper to keep the frame rate up. Bedrock's
  logic is native, so this version measures real fps and lowers/raises `gfx_viewdistance` in `options.txt`. Applies next launch.
* **FPS HUD** (`java/mods/fps-hud`): shows fps, frame time and heap.
* Not rewritable from Java alone: Sodium/Iris (renderer), BetterHitreg (needs the `player.attack` hook), 3D Skin Layers
  (needs a renderer hook), Mod Menu (this project's Mods panel replaces it).
