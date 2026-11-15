# Customizing a mod (settings)

In `onLoad`, declare options:

    ctx.config.toggle("logTicks", "Log tick counter", true)
              .number("logEverySeconds", "Log every (seconds)", 60, 5, 300)
              .text("greeting", "Greeting text", "Hello!");

Read them any time: `ctx.config.getBoolean("logTicks")`, `getDouble(...)`, `getString(...)`.
React to changes with `Events.on("config.changed", (name, data) -> ...)` (data is `key=value`).

Players change them in-game: **Mods button > Settings** beside the mod. Toggles and sliders save instantly.
Text boxes need the on-screen keyboard, which may not open over the game on every device; if it doesn't,
edit `<dataDir>/data/<mod id>/config.properties` instead.
