# modhost-java-bridge (LeviLauncher Android)

Runs Java mods inside Minecraft Bedrock on Android through a native LeviLauncher mod.
Mods are **rewritten for Bedrock** with the new Bedrock mod API (`com.modhost.bedrock`, see docs/BEDROCK_API.md):
game state, HUD, options and native hooks. The `.levipack` is named `modhost-java-bridge.levipack`.
The old Fabric/NeoForge compatibility layer is still there for jars that only need events and the loader.

    C++ mod (template) -> JavaBridge -> DexClassLoader -> com.modhost.api.Bootstrap -> your ModHostMod

## How the build works
CI checks out the LeviLauncher template next to this repo, runs `scripts/overlay.py` (adds `src/bridge`,
hooks it into `MyMod.cpp` and `CMakeLists.txt`, ships `modhost-api.dex.jar` inside the `.levipack`),
then runs the template's `scripts/package.ps1 -Abi arm64-v8a`. Nothing to merge by hand.
Minecraft version filter is the template's default `1.26.*`.

## Writing a mod
Implement `BedrockMod` (or `ModHostMod`), build with javac + d8 (see `scripts/build-java.sh`), and put
`yourmod.jar` + `yourmod.json` (`{"id": "...", "main": "pkg.Class"}`) into `<dataDir>/mods`.
Jars must be dexed with d8; Android can't load plain class-file jars.

## Features
- New Mods screen: two panes (search, sort, library toggle, mod cards with badges; details, Config, Log), same dark theme as the LeviLauncher Mods screen
- The **Mods** button can be dragged anywhere; its position is saved
- Bedrock API + rewritten mods: Lithium for Bedrock, FPS HUD (docs/BEDROCK_API.md)
- Events: tick (20/s), app.resumed/app.paused, custom events, native `fire()` (docs/EVENTS.md)
- In-game "Mods" button: switch mods on/off, see why a mod can't run, and open a gear editor per mod:
  status, settings, and a text editor for its config files (docs/CONFIG.md)
- Fabric-style and NeoForge-style APIs: `ModInitializer`, `FabricLoader`, `Event`, `ClientTickEvents`,
  `@Mod` + `IEventBus`, slf4j (docs/API_COMPAT.md)
- Bundled libraries so common mods load: slf4j, log4j2 (shims), Gson (downloaded at build)
- `Copy report` button for sharing status and log without screenshots
- `convert-mods` workflow prepares ordinary jars (docs/COMPAT.md)

## Status
- Done: JVM bridge, API, events, menu + gear editor, Fabric/NeoForge-style layer, converter, CI.
- Native hooks: `world.join`, `world.leave`, `player.login` located in the supplied libminecraftpe.so and wired (docs/HOOKS.md). Still open: attack, chat, block events.
- Not done: Lua loader.
- Untested on a device or inside the real template; expect small fixes.
