package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * Hunter's Bow - Bouncy Arrows. Shift while drawing the bow to charge the next
 * arrow with up to {@code 3} bounces (each shift adds one and wraps back to
 * zero). Charged arrows ricochet off blocks until their charges are spent.
 * Runtime behavior lives in {@code HunterBowBehavior}.
 */
public class BouncyArrowAbility extends Ability {

    public static final String ID = "BOUNCY_ARROWS";

    public BouncyArrowAbility() {
        super(ID);
    }

    @Override
    public String getId() {
        return ID;
    }
}
