package dev.core.ability;

/**
 * The cooldown interface handed to an {@link Effect} at cast time. An effect
 * decides WHEN the cooldown starts (cast, hit, return, ...) while the manager
 * decides HOW LONG it lasts (config value, haste scaling, ...), so effects
 * never re-implement cooldown math themselves.
 */
public interface CooldownSink {

	/**
	 * Start the cooldown for the ability's configured duration, scaled per the
	 * ability's {@link Ability#getCooldownScaling()}.
	 */
	void startCooldown();

	/**
	 * Start the cooldown with a custom raw duration in milliseconds. No scaling
	 * is applied; the caller owns any stat interaction (e.g. a shatter penalty).
	 */
	void startCooldown(long millis);

	/**
	 * Cancel any running cooldown for this cast (e.g. the bonemerang returned,
	 * so no penalty applies).
	 */
	void clearCooldown();

	/**
	 * @return milliseconds remaining on this cast's cooldown, 0 when none.
	 */
	long remainingCooldown();

}
