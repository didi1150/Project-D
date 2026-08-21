package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * Soul Collector — the Soul Tome's passive ability. While holding the tome, the
 * Support player captures dropped soul skulls by simply running into them. The
 * mechanic itself runs on the skull side (proximity scan); this ability only
 * carries the id, description and PASSIVE trigger type so it is displayed on
 * the item lore like any other passive.
 */
public class SoulCollectorAbility extends Ability {

	public SoulCollectorAbility() {
		super("SOUL_COLLECTOR");
	}

	@Override
	public String getId() {
		return "SOUL_COLLECTOR";
	}
}