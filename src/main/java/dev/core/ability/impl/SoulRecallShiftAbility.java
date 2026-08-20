package dev.core.ability.impl;

/**
 * Soul Store (shift-right-click) — the Soul Tome's store-back trigger. Dismisses
 * every active summon and puts its captured soul straight back into the tome,
 * so a Support can carry their souls between fights. The behavior lives in
 * {@code BukkitSoulStoreEffect}, bound to the {@code SOUL_RECALL_SHIFT} id.
 */
public class SoulRecallShiftAbility extends SoulRecallAbility {

	public SoulRecallShiftAbility() {
		super("SOUL_RECALL_SHIFT");
	}
}