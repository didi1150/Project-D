package dev.core.ability;

import dev.core.entity.RPGEntity;
import dev.core.event.Event;

public interface Ability {

	String getId();

	String getName();

	String getDescription();

	AbilityTriggerType getTriggerType();
	
	Event getTriggerEvent();

	AbilityAction getAction();

	Effect activate(RPGEntity caster);

	/**
	 * Cooldown in millis
	 */
	long getCooldown();

	AbilityCost getCost();

}
