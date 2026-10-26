package dev.core.ability.impl;

import dev.core.ability.Ability;
import dev.core.ability.AbilityAction;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.CooldownScope;

public class SniperPassiveAbility extends Ability {

    public static final String ID = "SNIPER_PASSIVE";

    public SniperPassiveAbility() {
        super(ID);
        setTriggerType(AbilityTriggerType.PASSIVE);
        setAction(AbilityAction.NONE);
        setScope(CooldownScope.PLAYER);
        setCooldown(0L);
    }

    @Override
    public String getId() {
        return ID;
    }
}
