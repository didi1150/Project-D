package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * Passive ability for the Slime Boots reward item. While the boots are worn,
 * the matching Bukkit behavior launches the player forward when they sprint
 * and jump from the ground.
 */
public class SlimeBootsAbility extends Ability {

    public static final String ID = "SLIME_BOOTS";

    public SlimeBootsAbility() {
        super(ID);
    }
}
