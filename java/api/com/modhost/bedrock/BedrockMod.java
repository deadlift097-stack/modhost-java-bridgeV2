package com.modhost.bedrock;

import com.modhost.api.ModHostMod;

/**
 * A mod written for Bedrock, not ported from Java Edition. Implement this, then use the {@link Bedrock}
 * facade (game state, HUD, options, native hooks) and {@code Events} (tick, world.join, player.attack, ...).
 * Point "bedrock" in the mod's .json at the class: {"id":"my_mod","bedrock":"pkg.MyMod"}.
 */
public interface BedrockMod extends ModHostMod {}
