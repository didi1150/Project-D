package dev.core.ability.impl;

import dev.core.ability.Ability;
import dev.core.ability.AbilityAction;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.CooldownScope;

/**
 * Assassin sword — Blade Dance.
 * Passive stack: floating iron swords orbit chest, 1 per 5s up to 5.
 * Active (right-click): all stacks fly forward in a cone, each dealing
 * 20 + 30% ATTACK_DAMAGE + 20% LETHALITY as physical damage to enemies
 * they pass through. Handled by {@code BladeDanceBehavior} (stack) +
 * {@code BukkitBladeDanceEffect} (cone).
 */
public class BladeDanceAbility extends Ability {

    public static final String ID = "BLADE_DANCE";

    public BladeDanceAbility() {
        super(ID);
        setTriggerType(AbilityTriggerType.MANUAL);
        setAction(AbilityAction.RIGHT_CLICK);
        setScope(CooldownScope.PLAYER);
        setCooldown(0L);
    }

    @Override
    public String getId() {
        return ID;
    }
}
