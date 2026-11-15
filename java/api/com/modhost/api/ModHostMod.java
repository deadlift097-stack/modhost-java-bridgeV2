package com.modhost.api;

/** Implement this and point "main" in your mod's .json at the class. */
public interface ModHostMod {
    void onLoad(ModContext ctx);
    void onEnable();
    void onDisable();
}
