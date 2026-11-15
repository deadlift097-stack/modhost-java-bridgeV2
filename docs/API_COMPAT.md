# Fabric / NeoForge compatibility layer

Mods written in the Fabric or NeoForge style can use the same entrypoints and event patterns here.
What the layer gives you is the *shape* of those APIs. Game objects (blocks, items, entities, rendering)
are Java Edition code and do not exist on Bedrock, so mods that touch them are shown as
"Java Edition only" in the Mods menu and stay off.

## Fabric
| Fabric API | Status |
|---|---|
| `ModInitializer`, `ClientModInitializer` | works |
| `FabricLoader.getInstance()`: `isModLoaded`, `getAllMods`, `getModContainer`, `getConfigDir`, `getGameDir` | works (`getConfigDir()` = `<dataDir>/data/config`) |
| `Event<T>`, `EventFactory.createArrayBacked` | works (handlers of disabled mods are skipped) |
| `ClientTickEvents.START/END_CLIENT_TICK`, `ClientLifecycleEvents.CLIENT_STARTED` | works, 20 ticks/s on the Android UI thread |
| `@Environment(EnvType.CLIENT)` | works |
| slf4j `Logger` / `LoggerFactory`, log4j2 `Logger` / `LogManager` | works (goes to logcat and the menu log) |
| Gson (`com.google.gson.*`) | bundled into the API dex by the build (downloaded from Maven Central, version 2.11.0) |
| `ModContainer.getMetadata()` -> `net.fabricmc.loader.api.metadata.ModMetadata` | works (id, name, version, description) |
| `net.minecraft.client.Minecraft` | empty stand-in: `getInstance()`, `execute(Runnable)` |
| Mixins, registries, rendering, networking, commands | not possible (no Java Edition code to patch) |

## NeoForge
| NeoForge API | Status |
|---|---|
| `@Mod("id")` with constructor taking `IEventBus`, `ModContainer`, `Dist` | works |
| `IEventBus.register(obj)` with `@SubscribeEvent`, `post`, `addListener(Class, Consumer)` | works |
| `addListener(Consumer)` without a class | works, but the listener sees every event and ignores ones it cannot cast |
| `NeoForge.EVENT_BUS`, `ClientTickEvent.Pre/Post` | works |
| `FMLCommonSetupEvent`, `FMLClientSetupEvent` | works (posted once when the mod is enabled) |
| `DeferredRegister`, `ModConfigSpec`, capabilities, networking | not available (use `ctx.config` for settings) |

## ModHost-native API
`ModHostMod`, `ModContext`, `ctx.config` (settings), `Events` (named events), `Scheduler`
(`runOnMain`, `runLater`). Use this for mods written for Bedrock from the start.

## When a mod fails
If loading or a handler throws, ModHost classifies it once and stops calling that mod:
- "Java Edition only (needs net.minecraft...)": the mod uses game classes Bedrock does not have.
- "missing library x.y.Z": a plain library is absent; add it to `java/api` as a shim, or bundle it like Gson in `scripts/build-java.sh`.
- "unsupported API call: ...": the mod calls a Fabric/NeoForge method this layer does not have yet. Add it to the shim.
The reason appears under the mod in the menu and in the gear editor.
