package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * Shield Bash - the Bulwark's shift-right-click ability. Swings the shield
 * through the half-circle in front of the caster and stuns every enemy inside;
 * the rest of the metadata (name, description, trigger action, cooldown) lives
 * in abilities.yml.
 */
public class ShieldBashAbility extends Ability {

	public ShieldBashAbility() {
		super("SHIELD_BASH");
	}

	@Override
	public String getId() {
		return "SHIELD_BASH";
	}
}