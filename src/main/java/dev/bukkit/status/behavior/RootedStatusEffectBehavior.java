package dev.bukkit.status.behavior;

/**
 * Soft CC: pins the target in place (move speed driven to ~0 via the
 * stat-engine modifier, or a high-amplitude SLOWNESS potion on vanilla-only
 * entities). Attacking and casting still work.
 */
public final class RootedStatusEffectBehavior extends AbstractSpeedStatusEffectBehavior {

	@Override
	protected double moveSpeedFactor() {
		return 0.001;
	}

	@Override
	protected int slownessAmplifier() {
		return 6;
	}

	@Override
	protected String sourceId() {
		return "status_rooted";
	}
}