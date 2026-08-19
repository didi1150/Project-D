package dev.core.ability.impl;

import dev.core.ability.Ability;
import dev.core.ability.AbilityCost;

/**
 * Soul Summon — the Soul Tome's right-click ability. Summons the most recently
 * captured soul as a player-owned ally. The 25 mana cost is enforced here so it
 * survives whatever the config metadata sets; the rest of the metadata (name,
 * description, cooldown) lives in abilities.yml.
 */
public class SoulSummonAbility extends Ability {

	private static final double MANA_COST = 25;

	public SoulSummonAbility() {
		super("SOUL_SUMMON", null, AbilityCost.manaCost(MANA_COST));
	}

	@Override
	public String getId() {
		return "SOUL_SUMMON";
	}
}