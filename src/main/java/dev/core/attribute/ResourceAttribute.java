package dev.core.attribute;

import java.util.function.Supplier;

public class ResourceAttribute {
	private final ResourceType type;
	private double current;
	private final Supplier<Float> maxSupplier;
	private final Supplier<Float> regenSupplier;
	private double timeSinceLastRegen;
	private double lastMax;

	public ResourceAttribute(ResourceType type, Supplier<Float> maxSupplier, Supplier<Float> regenSupplier) {
		this.type = type;
		this.maxSupplier = maxSupplier;
		this.regenSupplier = regenSupplier;
		this.lastMax = maxSupplier.get();
		this.current = lastMax; // Start at full resource
	}

	// Called every tick
	public void update(double deltaTime) {
		updateMaxValue();
		applyRegeneration(deltaTime);
	}

	private void updateMaxValue() {
		double newMax = maxSupplier.get();
		if (newMax != lastMax) {
			// Maintain percentage when max changes
			double percentage = current / lastMax;
			current = newMax * percentage;
			lastMax = newMax;
		}
	}

	private void applyRegeneration(double deltaTime) {
		timeSinceLastRegen += deltaTime;

		// Convert 5-second regen to per-second
		double regenInterval = 1.0; // Seconds
		if (timeSinceLastRegen >= regenInterval) {
			double regenPer5Sec = regenSupplier.get();
			double regenPerTick = (regenPer5Sec / 5.0) * timeSinceLastRegen;

			current = Math.min(lastMax, current + regenPerTick);
			timeSinceLastRegen = 0;
		}
	}

	// External modifications (damage/healing)
	public void modify(double amount) {
		current = Math.max(0, Math.min(lastMax, current + amount));
	}

	public void set(double value) {
		current = Math.max(0, Math.min(lastMax, value));
	}

	public double getCurrent() {
		return current;
	}

	public double getMax() {
		return lastMax;
	}

	public double getPercentage() {
		return current / lastMax;
	}

	public boolean isFull() {
		return current >= lastMax;
	}

	public boolean isEmpty() {
		return current <= 0;
	}

	public ResourceType getType() {
		return type;
	}

}
