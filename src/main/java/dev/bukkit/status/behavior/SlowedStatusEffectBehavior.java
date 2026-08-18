package dev.bukkit.status.behavior;

/**
 * Soft CC: reduces movement speed (stat-engine {@code MOVE_SPEED} modifier on
 * RPG entities, SLOWNESS potion fallback) but leaves attacking and casting
 * untouched. Potency of 0.6 keeps 60% of the target's speed.
 */
public final class SlowedStatusEffectBehavior extends AbstractSpeedStatusEffectBehavior {

	@Override
	protected double moveSpeedFactor() {
		return 0.6;
	}

	@Override
	protected int slownessAmplifier() {
		return 4;
	}

	@Override
	protected String sourceId() {
		return "status_slowed";
	}
}