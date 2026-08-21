package dev.core.ability;

import java.util.ArrayList;
import java.util.List;

import dev.core.storage.config.ConfigSection;

/**
 * The cast cost(s) of an ability. Costs are mode-based ({@link CostMode} +
 * amount) so the effect manager and lore consume them uniformly; entries may
 * carry a {@link CostFormula} for dynamic amounts resolved against the caster
 * at cast time. Legacy resource-string APIs (e.g. {@link #manaCost}) are
 * mapped onto the same entries.
 */
public class AbilityCost {

	private final List<CostEntry> costs;

	public AbilityCost() {
		this.costs = new ArrayList<>();
	}

	public AbilityCost(CostMode mode, double amount) {
		this();
		addCost(mode, amount);
	}

	public AbilityCost(CostEntry entry) {
		this();
		addCost(entry);
	}

	public AbilityCost(String resourceType, double amount) {
		this(CostMode.fromResourceType(resourceType), amount);
	}

	/**
	 * Loads a cost from a config section:
	 *
	 * <pre>{@code
	 * cost:
	 *   mode: MANA        # MANA or HEALTH
	 *   amount: 25        # flat cost
	 *   formula: "10 + 0.05 * MANA_MAX"   # dynamic cost, overrides amount
	 * }</pre>
	 *
	 * When {@code formula} is present it is syntax-checked here (parse errors
	 * throw) and evaluated against the caster at cast time. A section with no
	 * chargeable component yields a no-cost instance.
	 */
	public static AbilityCost fromConfig(ConfigSection section) {
		CostMode mode = CostMode.valueOf(section.getString("mode", CostMode.MANA.name()).toUpperCase());
		double amount = section.getDouble("amount", 0.0);
		String formulaSource = section.getString("formula", null);
		CostFormula formula = formulaSource == null || formulaSource.isBlank() ? null : CostFormula.parse(formulaSource);
		return new AbilityCost(new CostEntry(mode, amount, formula));
	}

	public void addCost(CostMode mode, double amount) {
		addCost(new CostEntry(mode, amount));
	}

	/**
	 * Adds an entry, merging into the existing same-mode entry when present:
	 * flat amounts sum; for formulas the first configured one wins. Entries
	 * without any chargeable component are dropped.
	 */
	public void addCost(CostEntry entry) {
		if (entry == null || !entry.hasComponent()) {
			return;
		}
		for (int i = 0; i < costs.size(); i++) {
			CostEntry existing = costs.get(i);
			if (existing.mode() == entry.mode()) {
				costs.set(i, new CostEntry(entry.mode(), existing.amount() + entry.amount(),
						existing.formula() != null ? existing.formula() : entry.formula()));
				return;
			}
		}
		costs.add(entry);
	}

	public void addCost(String resourceType, double amount) {
		addCost(CostMode.fromResourceType(resourceType), amount);
	}

	public List<CostEntry> getCosts() {
		return new ArrayList<>(costs);
	}

	/**
	 * The summed flat amounts configured for the mode. Formula-driven
	 * components are NOT included here (they need a caster); consumers charging
	 * or displaying real costs use {@link CostEntry#resolve} over
	 * {@link #getCosts()}.
	 */
	public double getCost(CostMode mode) {
		for (CostEntry entry : costs) {
			if (entry.mode() == mode) {
				return entry.amount();
			}
		}
		return 0.0;
	}

	public boolean hasCost() {
		return !costs.isEmpty();
	}

	public boolean hasCost(CostMode mode) {
		return getCost(mode) > 0;
	}

	// Static factory methods for common cost types
	public static AbilityCost noCost() {
		return new AbilityCost();
	}

	public static AbilityCost healthCost(double amount) {
		return new AbilityCost(CostMode.HEALTH, amount);
	}

	public static AbilityCost manaCost(double amount) {
		return new AbilityCost(CostMode.MANA, amount);
	}

	// Builder pattern for complex costs
	public static class Builder {
		private final AbilityCost cost = new AbilityCost();

		public Builder health(double amount) {
			cost.addCost(CostMode.HEALTH, amount);
			return this;
		}

		public Builder mana(double amount) {
			cost.addCost(CostMode.MANA, amount);
			return this;
		}

		public Builder custom(String resourceType, double amount) {
			cost.addCost(resourceType, amount);
			return this;
		}

		public AbilityCost build() {
			return cost;
		}
	}

	public static Builder builder() {
		return new Builder();
	}

}