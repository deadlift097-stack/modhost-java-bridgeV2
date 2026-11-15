package com.modhost.bedrock;

/** Entry point of the Bedrock mod API. Everything here talks to the running Bedrock game. */
public final class Bedrock {
    private Bedrock() {}

    /** Live game state: fps, frame time, foreground, screen size, worlds on disk. */
    public static Game game() { return Game.INSTANCE; }

    /** Text lines and toasts drawn over the game. */
    public static Hud hud() { return Hud.INSTANCE; }

    /** Read and write Bedrock's options.txt (takes effect when the game reloads its options). */
    public static Options options() { return Options.INSTANCE; }

    /** Native hooks into Bedrock's own code (player.attack, ...). */
    public static Hooks hooks() { return Hooks.INSTANCE; }
}
