package dev.core.ability.impl;

import dev.core.ability.Ability;
import dev.core.ability.AbilityTriggerType;

/**
 * Orb of Stealth passive — 20% less chance to be noticed by enemy entities.
 * PASSIVE: runtime in StealthPassiveBehavior.
 */
public class OrbStealthPassiveAbility extends Ability {

    public static final String ID = "ORB_STEALTH_PASSIVE";

    public OrbStealthPassiveAbility() {
        super(ID);
        setTriggerType(AbilityTriggerType.PASSIVE);
    }

    @Override
    public String getId() {
        return ID;
    }
}
