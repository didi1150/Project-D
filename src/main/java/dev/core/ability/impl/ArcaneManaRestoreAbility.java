package dev.core.ability.impl;

import dev.core.ability.Ability;
import dev.core.ability.AbilityTriggerType;

/**
 * Arcane Blade passive — Arcane Siphon.
 * Restores 5% of max mana on every successful melee hit while the blade is equipped.
 * This is a {@link AbilityTriggerType#PASSIVE} ability: it has no active cast,
 * no cooldown and no cost. The runtime handling lives in the per-holder
 * {@code ArcaneManaRestoreBehavior}.
 * Lore is driven from {@code abilities.yml} (PASSIVE) and rendered as
 * "Passive: Arcane Siphon" on the item.
 */
public class ArcaneManaRestoreAbility extends Ability {

    public static final String ID = "ARCANE_MANA_RESTORE";

    public ArcaneManaRestoreAbility() {
        super(ID);
        setTriggerType(AbilityTriggerType.PASSIVE);
    }

    @Override
    public String getId() {
        return ID;
    }
}
