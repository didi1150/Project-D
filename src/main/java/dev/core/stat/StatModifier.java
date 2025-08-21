package dev.core.stat;

public class StatModifier {

	public final double amount;
	public final ModifierStackPolicy stackPolicy;

	public final ModifierType modifierType;
	public final StatType statType;
	public final String sourceId;
	public final double duration; // seconds; <= 0 means permanent
	public final double appliedAt; // Use Game Time, not System#currentMillis
	public final int maxStacks; // optional
	public final StatTarget statTarget;
	private int currentStacks;

	public StatModifier(double amount, ModifierStackPolicy stackPolicy, ModifierType modifierType, StatType statType,
			String sourceId, double duration, double appliedAt, int maxStacks, StatTarget statTarget) {
		this.amount = amount;
		this.stackPolicy = stackPolicy;
		this.modifierType = modifierType;
		this.statType = statType;
		this.sourceId = sourceId;
		this.duration = duration;
		this.appliedAt = appliedAt;
		this.maxStacks = maxStacks;
		this.statTarget = statTarget;
		this.currentStacks = 0;
	}

	public boolean expired(double now) {
		return duration > 0 && now >= appliedAt + duration;
	}

	// info
	public double effectiveAmount() {
		return amount * Math.max(1, currentStacks);
	}

	public int getCurrentStacks() {
		return currentStacks;
	}

	public void applyStack(int stacks) {
		this.currentStacks = Math.min(maxStacks, this.currentStacks + stacks);
	}
}
