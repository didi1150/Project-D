package dev.core.ability.impl;

import dev.core.ability.Ability;
import dev.core.ability.AbilityAction;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.CooldownScope;

public class WitherSkullLaunchAbility extends Ability {

    public static final String ID = "WITHER_SKULL_LAUNCH";

    public WitherSkullLaunchAbility() {
        super(ID);
        setTriggerType(AbilityTriggerType.MANUAL);
        setAction(AbilityAction.LEFT_CLICK);
        setScope(CooldownScope.PLAYER);
        setCooldown(0L);
    }

    @Override
    public String getId() {
        return ID;
    }

}
