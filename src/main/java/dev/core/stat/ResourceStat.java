package dev.core.stat;

import java.util.function.Supplier;

public class ResourceStat extends Stat {

	private Supplier<Double> currentSupplier;
	private Supplier<Double> maxSupplier;

	private ModifierBucket maxModifierBucket;

	public ResourceStat(String name, Supplier<Double> maxSupplier, Supplier<Double> currentSupplier) {
		super(name, currentSupplier.get(), maxSupplier.get());
		this.maxSupplier = maxSupplier;
		this.currentSupplier = currentSupplier;
		this.maxModifierBucket = new ModifierBucket();
	}

	@Override
	public double getCurrent(long now) {
		return modifierBucket.getFinalValue(currentSupplier.get(), now);
	}

	@Override
	public double getMax(long now) {
		return maxModifierBucket.getFinalValue(maxSupplier.get(), now);
	}

	public void addMaxModifier(StatModifier statModifier) {
		maxModifierBucket.add(statModifier);
	}
}
