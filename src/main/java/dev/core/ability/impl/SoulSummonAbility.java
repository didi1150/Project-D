package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * Soul Summon — the Soul Tome's right-click ability. Summons every captured
 * soul as player-owned allies. All gameplay metadata, including the mana
 * cost, is configured in abilities.yml (see the {@code SOUL_SUMMON} entry);
 * this class only carries the id and effect wiring.
 */
public class SoulSummonAbility extends Ability {

	public SoulSummonAbility() {
		super("SOUL_SUMMON");
	}

	@Override
	public String getId() {
		return "SOUL_SUMMON";
	}
}
