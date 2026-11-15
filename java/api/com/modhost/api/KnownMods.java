package com.modhost.api;

import java.util.HashMap;
import java.util.Map;

/** Plain-language notes shown in the gear editor for well-known Java Edition mods. */
final class KnownMods {
    private static final Map<String, String> NOTES = new HashMap<String, String>();

    static {
        String render = "Rewrites Java Edition's OpenGL renderer. Bedrock draws with its own engine, so there is nothing for it to change.";
        String logic = "Optimizes Java Edition's game logic. Bedrock's game logic is separate native code that Java mods cannot reach.";
        NOTES.put("sodium", render);
        NOTES.put("sodium-extra", "Adds settings to Sodium, which only exists on Java Edition. " + render);
        NOTES.put("iris", "Shader loader for Sodium on Java Edition. Bedrock shaders come from resource packs / the game's own settings.");
        NOTES.put("indium", "Rendering add-on for Sodium. " + render);
        NOTES.put("immediatelyfast", render);
        NOTES.put("entityculling", render);
        NOTES.put("moreculling", render);
        NOTES.put("lithium", logic + " Rewritten for Bedrock as \"Lithium for Bedrock\": it tunes the game's own options from measured frame rate.");
        NOTES.put("ferritecore", "Reduces memory use of Java Edition's internal data. Bedrock does not use that data.");
        NOTES.put("starlight", "Rewrites Java Edition's light engine. Bedrock has its own.");
        NOTES.put("krypton", "Speeds up Java Edition's networking. Bedrock uses a different protocol.");
        NOTES.put("modmenu", "Lists Fabric mods in Java Edition's menu. The built-in Mods button in this project does that job.");
        NOTES.put("skinlayers3d", "Draws 3D skin layers on Java Edition's player model. Bedrock skins use a different system.");
        NOTES.put("betterhitreg", "Changes how Java Edition registers hits over its network protocol. Bedrock uses its own protocol, "
                + "so it cannot be applied. A Bedrock version needs a native hook on Bedrock's attack code.");
        NOTES.put("fabric-api", "A library for Java Edition mods. The parts that can be emulated (events, loader) are built in; "
                + "the rest needs Java Edition game code.");
    }

    private KnownMods() {}

    static String note(String id) { return NOTES.get(id); }
}
