package dev.core.ability.impl;

import dev.core.ability.Ability;
import dev.core.ability.AbilityAction;
import dev.core.ability.AbilityCost;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.CooldownScope;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;
import dev.core.event.Event;

public class ParticleTestAbility implements Ability {

	public ParticleTestAbility() {
	}

	@Override
	public String getId() {
		return "PARTICLE_TEST_ABILITY";
	}

	@Override
	public String getName() {
		return "Particle Test";
	}

	@Override
	public String getDescription() {
		return "Test Particles";
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
		return 1000;
	}

	@Override
	public AbilityCost getCost() {
		return AbilityCost.noCost();
	}

	@Override
	public CooldownScope getScope() {
		return CooldownScope.PLAYER;
	}
}
