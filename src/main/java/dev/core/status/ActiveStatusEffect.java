package dev.core.status;

import java.util.UUID;

/**
 * One active status-effect instance on an entity. Instances are created by
 * {@link StatusEffectManager} on apply and are immutable; a re-apply replaces
 * the instance (fresh duration) rather than mutating it, so display handles
 * keyed by instance never go stale.
 */
public final class ActiveStatusEffect {

	private final StatusEffectType type;
	private final long startTime;
	private final long endTime; // -1 when infinite
	private final boolean fadeOut;
	private final double potency;
	private final UUID casterUuid;

	public ActiveStatusEffect(StatusEffectType type, long startTime, long endTime, boolean fadeOut, double potency) {
		this(type, startTime, endTime, fadeOut, potency, null);
	}

	public ActiveStatusEffect(StatusEffectType type, long startTime, long endTime, boolean fadeOut, double potency,
			UUID casterUuid) {
		this.type = type;
		this.startTime = startTime;
		this.endTime = endTime;
		this.fadeOut = fadeOut;
		this.potency = potency;
		this.casterUuid = casterUuid;
	}

	/**
	 * @return true once the duration has passed. Infinite effects (endTime == -1)
	 *         never expire.
	 */
	public boolean hasExpired(long now) {
		return endTime > 0 && now >= endTime;
	}

	/**
	 * @return remaining milliseconds, or -1 for infinite effects.
	 */
	public long remaining(long now) {
		return endTime < 0 ? -1 : Math.max(0, endTime - now);
	}

	public StatusEffectType getType() {
		return type;
	}

	public long getStartTime() {
		return startTime;
	}

	public long getEndTime() {
		return endTime;
	}

	/**
	 * Whether the display should fade out over its last moments instead of
	 * disappearing outright when the effect ends.
	 */
	public boolean isFadeOut() {
		return fadeOut;
	}

	/**
	 * Effect strength, interpreted per type (e.g. {@code 0.6} for SLOWED = keep
	 * 60% movement speed). Default 1.0.
	 */
	public double getPotency() {
		return potency;
	}

	/**
	 * The UUID of the entity that applied this effect, or {@code null} for
	 * environment/applied effects. Used by damage-over-time behaviors (e.g.
	 * WITHER) to scale damage with the caster's stats.
	 */
	public UUID getCasterUuid() {
		return casterUuid;
	}
}