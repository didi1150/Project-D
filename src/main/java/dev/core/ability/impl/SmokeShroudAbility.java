package dev.core.ability.impl;

import dev.core.ability.Ability;
import dev.core.ability.AbilityAction;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.CooldownScope;

/**
 * Orb of Stealth active — Smoke Shroud.
 * Place a cloud of smoke around you that dissipates after ~6 seconds.
 * While inside you are invisible to enemy entities; re-entering clears aggro.
 * Attacking or leaving reveals you briefly (~1.5s).
 */
public class SmokeShroudAbility extends Ability {

    public static final String ID = "SMOKE_SHROUD";

    public SmokeShroudAbility() {
        super(ID);
        setTriggerType(AbilityTriggerType.MANUAL);
        setAction(AbilityAction.RIGHT_CLICK);
        setScope(CooldownScope.PLAYER);
        setCooldown(18000L); // default 18s, overridden by abilities.yml
    }

    @Override
    public String getId() {
        return ID;
    }
}
