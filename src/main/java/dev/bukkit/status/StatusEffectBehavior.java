package dev.bukkit.status;

/**
 * Per-type gameplay hook for a status effect. Behaviors are stateless
 * singletons registered in {@link StatusEffectBehaviorRegistry} under their
 * {@link dev.core.status.StatusEffectType}; any per-entity bookkeeping they
 * need (original attribute values, modifiers to restore, ...) must be keyed by
 * entity uuid and cleaned up in {@link #onEnd}.
 */
public interface StatusEffectBehavior {

	/**
	 * Called once when the effect is applied. Must apply the CC to the vanilla
	 * entity (potion effect, attribute sync, AI freeze, velocity ...).
	 */
	void onApply(StatusEffectContext ctx);

	/** Called every game tick while the effect is active. */
	void onTick(StatusEffectContext ctx, long now);

	/**
	 * Called once when the effect ends (expiry, cleanse, removal). Must fully
	 * undo whatever {@link #onApply} did.
	 */
	void onEnd(StatusEffectContext ctx);
}