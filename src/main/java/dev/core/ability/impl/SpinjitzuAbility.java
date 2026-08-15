package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * Spinjitzu — the Iron Hopper's right-click ability. Summons a cyclone that
 * pulls nearby enemies into the caster; the rest of the metadata (name,
 * description, trigger action, cooldown) lives in abilities.yml.
 */
public class SpinjitzuAbility extends Ability {

	public SpinjitzuAbility() {
		super("SPINJITZU");
	}

	@Override
	public String getId() {
		return "SPINJITZU";
	}
}