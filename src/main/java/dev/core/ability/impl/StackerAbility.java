package dev.core.ability.impl;

import dev.core.ability.Ability;
import dev.core.ability.AbilityTriggerType;

/**
 * Drain Blade passive — Stacker.
 * Grants +1 Attack Damage for every 25 Max Health the wielder has while the
 * blade is equipped. This is a {@link AbilityTriggerType#PASSIVE} ability: it
 * has no active cast, no cooldown and no cost. The bonus is a real stat
 * modifier: {@code StackerBehavior} keeps a flat ATTACK_DAMAGE modifier on the
 * holder's stats (re-derived from Max Health every tick), so it flows through
 * the StatEngine like any other ATTACK_DAMAGE source and is removed the
 * moment the blade is unequipped.
 * Lore is driven from {@code abilities.yml} (PASSIVE) and rendered as
 * "Passive: Stacker" on the item.
 */
public class StackerAbility extends Ability {

    public static final String ID = "STACKER";

    public StackerAbility() {
        super(ID);
        setTriggerType(AbilityTriggerType.PASSIVE);
    }

    @Override
    public String getId() {
        return ID;
    }
}
