package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * Focus Beam — the Arcane Focus staff's right-click ability. Channels a short
 * inward-spiraling charge, then fires an arcane beam that damages the first
 * enemy in line of sight every tick; casting again while the beam is up
 * cancels it (the cancelling click's cost is refunded, see the Bukkit effect).
 * All gameplay metadata, including the mana cost, is configured in
 * abilities.yml (see the {@code FOCUS_BEAM} entry); this class only carries
 * the id and effect wiring.
 */
public class FocusBeamAbility extends Ability {

	public static final String ID = "FOCUS_BEAM";

	public FocusBeamAbility() {
		super(ID);
	}

	@Override
	public String getId() {
		return ID;
	}
}
