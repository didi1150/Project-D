package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * Hunter's Bow - Explosive Arrows. Left-click while the bow is equipped to arm
 * the next arrow: it detonates once it has used up all of its bounces (or on
 * its first impact when uncharged). Runtime behavior lives in
 * {@code dev.bukkit.item.HunterBowManager}.
 */
public class ExplosiveArrowAbility extends Ability {

    public static final String ID = "EXPLOSIVE_ARROWS";

    public ExplosiveArrowAbility() {
        super(ID);
    }

    @Override
    public String getId() {
        return ID;
    }
}
