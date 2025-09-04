package dev.core.ability.impl;

import dev.core.ability.Ability;
import dev.core.ability.AbilityAction;
import dev.core.ability.AbilityCost;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;
import dev.core.event.Event;

public class SwingBoneAbility implements Ability {

	public SwingBoneAbility() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public String getId() {
		return "BONE_SWING";
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "Swing";
	}

	@Override
	public String getDescription() {
		return "Throw bone a short distance, dealing the damage an arrow would.\r\n" + "\r\n"
				+ "Deals double damage when coming back. Pierces up to 10 foes";
	}

	@Override
	public AbilityTriggerType getTriggerType() {
		return AbilityTriggerType.MANUAL;
	}

	@Override
	public Event getTriggerEvent() {
		return null;
	}

	@Override
	public AbilityAction getAction() {
		return AbilityAction.RIGHT_CLICK;
	}

	@Override
	public Effect activate(RPGEntity caster) {
		return null;
	}

	@Override
	public long getCooldown() {
		return 3000;
	}

	@Override
	public AbilityCost getCost() {
		return AbilityCost.noCost();
	}

}
