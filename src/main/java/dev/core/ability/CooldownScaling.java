package dev.core.ability;

/**
 * How an ability's cooldown duration is derived from the caster's stats.
 * <ul>
 * <li>{@link #HASTE} - the configured cooldown is reduced by the caster's
 * {@code ABILITY_HASTE} stat.</li>
 * <li>{@link #NONE} - the configured cooldown is applied as-is.</li>
 * </ul>
 */
public enum CooldownScaling {

	HASTE, NONE;

}