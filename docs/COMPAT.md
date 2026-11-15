# Running Java mods without rewriting them

1. Put the `.jar` in `mods-in/` in your repo, run the **convert-mods** workflow, download `converted-mods`.
2. Copy the `.jar` + `.json` pairs into `<dataDir>/mods` and restart the game.
3. Open the in-game **Mods** button. Each mod shows a gear icon, a status line, and a switch.

Fabric entrypoints and NeoForge `@Mod` classes are both detected (see API_COMPAT.md for what is supported).
Mods that need Minecraft Java Edition game code (Mixins, `net.minecraft.*` classes) cannot work on Bedrock:
they appear as "Java Edition only" and stay off. Sodium, Lithium, Sodium Extra, BetterHitreg and similar
mods change how the Java engine renders, ticks or registers hits, and Bedrock has its own engine, so there
is nothing for them to change. REPORT.txt in the converted-mods artifact tells you which case each jar is.

## Troubleshooting
- Open **Mods > Copy report**. It copies every mod's status plus the last 80 log lines to the clipboard and saves
  them to `<dataDir>/data/modhost-report.txt`. Send that instead of screenshots.
- Tap a mod's gear to see a plain-language note on well-known mods (what they do on Java Edition and why they
  have no Bedrock equivalent).
- Orange = Java Edition only (cannot work on Bedrock). Red = something fixable: a missing library or an
  API call the compatibility layer lacks. Red ones are worth reporting.
