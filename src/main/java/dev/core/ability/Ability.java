package dev.core.ability;

import dev.core.entity.RPGEntity;

public interface Ability {

	String getId();

	String getName();

	String getDescription();

	AbilityTriggerType getTriggerType();

	AbilityAction getAction();

	Effect activate(RPGEntity caster);

	/**
	 * Cooldown in millis
	 */
	long getCooldown();

	AbilityCost getCost();

}
