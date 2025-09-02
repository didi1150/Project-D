package dev.core.ability;

import dev.core.entity.RPGEntity;
import dev.core.event.Event;

public class HealAbility implements Ability {

	public HealAbility() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;                                                                              
	}

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AbilityTriggerType getTriggerType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AbilityAction getAction() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Effect activate(RPGEntity caster) {
		return null;
	}

	@Override
	public long getCooldown() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public AbilityCost getCost() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Event getTriggerEvent() {
		return null;
	}

}
