package dev.core.ability;

import dev.core.entity.RPGEntity;

/**
 * A single ability cast cost: the resource {@link CostMode} plus either a
 * static {@code amount} or a dynamic {@link CostFormula} (abilities.yml
 * {@code formula: "10 + 0.05 * MANA_MAX"}). The amount actually paid is
 * resolved at cast time via {@link #resolve(RPGEntity)}; formulas fall back to
 * the static amount when no caster is available (e.g. lore rendering without a
 * holder).
 */
public record CostEntry(CostMode mode, double amount, CostFormula formula) {

	public CostEntry(CostMode mode, double amount) {
		this(mode, amount, null);
	}

	/**
	 * The cost this entry charges the given caster: the formula result for
	 * dynamic entries, the configured flat amount otherwise. Never negative.
	 */
	public double resolve(RPGEntity caster) {
		if (formula == null || caster == null) {
			return amount;
		}
		return Math.max(0, formula.evaluate(caster));
	}

	/**
	 * Whether this entry carries any chargeable component at all. Entries with
	 * only a zero flat amount and no formula are dropped when costs are built.
	 */
	public boolean hasComponent() {
		return amount > 0 || formula != null;
	}
}
