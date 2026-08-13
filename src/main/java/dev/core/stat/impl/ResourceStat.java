package dev.core.stat.impl;

import java.util.function.LongFunction;

import dev.core.stat.Stat;
import dev.core.stat.modifier.ModifierBucket;

/**
 * A class representing a resource of an entity <br>
 * Important: Modifying this class's {@link ModifierBucket} doesn't change its
 * stats, since they are derived from other combat stats
 */
public class ResourceStat extends Stat {

	private final LongFunction<Double> regenSupplier; // base regen per second
	private final LongFunction<Double> maxSupplier; // base max value
	private long lastTick;

	public ResourceStat(String name, LongFunction<Double> maxSupplier, LongFunction<Double> regenSupplier, long now) {
		super(name, maxSupplier.apply(now), maxSupplier.apply(now));
		this.maxSupplier = maxSupplier;
		this.regenSupplier = regenSupplier;
		this.lastTick = now;
	}

	@Override
	public synchronized double getMax(long now) {
		return maxSupplier.apply(now);
	}

	@Override
	public synchronized double getCurrent(long now) {
		double max = getMax(now);
		return max < 0 ? current : Math.min(current, max);
	}

	public synchronized void tick(long now) {
		if (lastTick == now) {
			return;
		}

		double elapsedSeconds = (now - lastTick) / 1000.0;

		// Calculate regeneration (convert from per 5 seconds to per second)
		double effectiveRegen = regenSupplier.apply(now);
		double regenPerSecond = effectiveRegen / 5.0;
		double regenAmount = regenPerSecond * elapsedSeconds;

		// Apply regeneration
		double currentValue = getCurrent(now);
		double maxValue = getMax(now);

		current = Math.min(maxValue, currentValue + regenAmount);

		// Update ratio
		lastTick = now;
	}
}
