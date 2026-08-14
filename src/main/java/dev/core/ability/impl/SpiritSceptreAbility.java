package dev.core.ability.impl;

import dev.core.ability.Ability;
import dev.core.ability.AbilityCost;

/**
 * Guided Bat — the Spirit Sceptre's right-click ability. Fires a red bat that
 * homes in on the caster's crosshair and explodes on impact. The 250 mana cost
 * is enforced here so it survives whatever the config metadata sets; the rest
 * of the metadata (name, description, trigger action, cooldown) lives in
 * abilities.yml.
 */
public class SpiritSceptreAbility extends Ability {

	private static final double MANA_COST = 25;

	public SpiritSceptreAbility() {
		super("GUIDED_BAT", null, AbilityCost.manaCost(MANA_COST));
	}

	@Override
	public String getId() {
		return "GUIDED_BAT";
	}
}