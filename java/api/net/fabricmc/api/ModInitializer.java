package net.fabricmc.api;

/** Compatibility shim: lets jars that only use this interface load without changes. */
public interface ModInitializer {
    void onInitialize();
}
