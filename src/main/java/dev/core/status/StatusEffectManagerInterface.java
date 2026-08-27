package dev.core.status;

import java.util.List;
import java.util.UUID;

import dev.core.entity.RPGEntity;

/**
 * Owner of an entity's active status effects. The core implementation
 * ({@link StatusEffectManager}) is pure Java and unit-testable; Bukkit
 * subclassing it adds vanilla entity manipulation and the stacked text
 * displays above the entity's name.
 */
public interface StatusEffectManagerInterface {

	/**
	 * Apply (or refresh) a status effect on the entity.
	 *
	 * @return {@code false} when the apply was rejected (CC immunity active, or
	 *         another hard CC already active).
	 */
	boolean apply(RPGEntity entity, StatusEffectType type, long durationMillis, boolean fadeOut, double potency);

	/**
	 * Apply with caster tracking: the caster UUID is stored on the effect so
	 * damage-over-time behaviors can scale damage with the caster's stats.
	 */
	boolean apply(RPGEntity entity, StatusEffectType type, long durationMillis, boolean fadeOut, double potency,
			UUID casterUuid);

	/**
	 * Apply (or refresh) with the type's default fade behavior and full potency.
	 */
	boolean apply(RPGEntity entity, StatusEffectType type, long durationMillis);

	/**
	 * Forcefully end an effect of the given type, if active.
	 *
	 * @return true if an effect was removed.
	 */
	boolean remove(RPGEntity entity, StatusEffectType type);

	boolean has(RPGEntity entity, StatusEffectType type);

	/**
	 * Whether the entity is under hard CC (stunned/airborne) and thus cannot
	 * attack or cast abilities.
	 */
	boolean hasHardCc(RPGEntity entity);

	/** Whether the entity's CC-immunity is currently active. */
	boolean isCcImmune(RPGEntity entity);

	/**
	 * Active effects in application order, most recently added last.
	 */
	List<ActiveStatusEffect> getActive(RPGEntity entity);

	/** End every active effect on the entity (displays and behaviors cleaned up). */
	void removeAll(RPGEntity entity);

	/** Advance durations and expire finished effects. Call every game tick. */
	void tick(long now);

	/** End every effect on every entity. */
	void cancelAll();
}