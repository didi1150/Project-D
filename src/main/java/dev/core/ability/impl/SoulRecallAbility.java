package dev.core.ability.impl;

import dev.core.ability.Ability;
import dev.core.ability.AbilityCost;

/**
 * Soul Recall — the Soul Tome's recall ability. Teleports every active
 * summon of the caster to the caster's position. No mana cost; the 2-second
 * cooldown lives in abilities.yml. The plain constructor registers the
 * left-click variant ({@code SOUL_RECALL}); a subclass carries the
 * shift-right-click variant ({@code SOUL_RECALL_SHIFT}).
 */
public class SoulRecallAbility extends Ability {

	public SoulRecallAbility() {
		this("SOUL_RECALL");
	}

	protected SoulRecallAbility(String id) {
		super(id, null, AbilityCost.noCost());
	}
}