package dev.core.ability;

import java.util.HashMap;
import java.util.Map;

// Credits to Claude
public class AbilityCost {

	private final Map<String, Double> resourceCosts;

	public AbilityCost() {
		this.resourceCosts = new HashMap<>();
	}

	public AbilityCost(String resourceType, double amount) {
		this();
		addCost(resourceType, amount);
	}

	public void addCost(String resourceType, double amount) {
		if (amount > 0) {
			resourceCosts.put(resourceType, amount);
		}
	}

	public Map<String, Double> getResourceCosts() {
		return new HashMap<>(resourceCosts);
	}

	public double getCost(String resourceType) {
		return resourceCosts.getOrDefault(resourceType, 0.0);
	}

	public boolean hasCost() {
		return !resourceCosts.isEmpty();
	}

	public boolean hasResourceCost(String resourceType) {
		return resourceCosts.containsKey(resourceType) && resourceCosts.get(resourceType) > 0;
	}

	// Static factory methods for common cost types
	public static AbilityCost noCost() {
		return new AbilityCost();
	}

	public static AbilityCost healthCost(double amount) {
		return new AbilityCost("HEALTH_RESOURCE", amount);
	}

	public static AbilityCost manaCost(double amount) {
		return new AbilityCost("MANA_RESOURCE", amount);
	}

	// Builder pattern for complex costs
	public static class Builder {
		private final AbilityCost cost = new AbilityCost();

		public Builder health(double amount) {
			cost.addCost("HEALTH_RESOURCE", amount);
			return this;
		}

		public Builder mana(double amount) {
			cost.addCost("MANA_RESOURCE", amount);
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
