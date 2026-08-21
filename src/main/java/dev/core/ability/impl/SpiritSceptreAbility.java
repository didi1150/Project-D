package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * Guided Bat — the Spirit Sceptre's right-click ability. Fires a red bat that
 * homes in on the caster's crosshair and explodes on impact. All gameplay
 * metadata, including the mana cost, is configured in abilities.yml (see the
 * {@code GUIDED_BAT} entry); this class only carries the id and effect wiring.
 */
public class SpiritSceptreAbility extends Ability {

	public SpiritSceptreAbility() {
		super("GUIDED_BAT");
	}

	@Override
	public String getId() {
		return "GUIDED_BAT";
	}
}
