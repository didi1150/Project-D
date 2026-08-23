package dev.core.ability.impl;

import dev.core.ability.Ability;
import dev.core.ability.AbilityTriggerType;

/**
 * Arcane Blade passive — Arcane Cleave.
 * Every third direct melee hit (ENTITY_ATTACK, sweep hits excluded) the blade
 * morphs to DIAMOND_SWORD and releases a forward-traveling half-circle magic
 * cleave that originates from the struck target and flies in the attacker's
 * look direction. Visualized with particles, damages enemies inside the wave.
 * This is a {@link AbilityTriggerType#PASSIVE} ability: no active cast,
 * runtime handling in the per-holder {@code ArcaneCleaveBehavior}.
 */
public class ArcaneCleaveAbility extends Ability {

    public static final String ID = "ARCANE_CLEAVE";

    public ArcaneCleaveAbility() {
        super(ID);
        setTriggerType(AbilityTriggerType.PASSIVE);
    }

    @Override
    public String getId() {
        return ID;
    }
}
