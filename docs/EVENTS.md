# Game events

Java side (any mod):
    Events.on("tick", (name, data) -> ...)        // 20 per second (timer on the Android UI thread)
    Events.on("app.resumed" / "app.paused", ...)  // game screen comes to front / goes away
    Events.post("my.event", "{\"x\":1}")          // invent your own events; any mod can listen
Register handlers in onLoad. Handlers of disabled mods are skipped automatically.

Native side (add a real game event):
    modhost::bridge().fire("player.hit", "{\"damage\":4}");
Call this from a detour made with the template's pl/memory/Hook.hpp. Hooks need Bedrock function
signatures for your exact game version (e.g. 1.26.62.1) which I could not verify, so none are included.
Once you have one hook, Java mods get the event with no further changes.
