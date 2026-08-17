package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * Smash — the Mace's right-click ability. Slams into the ground and sends a
 * shockwave in a cone in front of the caster; the rest of the metadata (name,
 * description, trigger action, cooldown) lives in abilities.yml.
 */
public class SmashAbility extends Ability {

	public SmashAbility() {
		super("SMASH");
	}

	@Override
	public String getId() {
		return "SMASH";
	}
}
