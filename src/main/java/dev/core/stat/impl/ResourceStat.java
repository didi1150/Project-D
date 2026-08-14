package dev.core.stat.impl;

import java.util.Map;
import java.util.function.LongFunction;

import dev.core.stat.Stat;
import dev.core.stat.StatType;
import dev.core.stat.adapter.StatTypeAdapter;
import dev.core.stat.engine.StatEngine;
import dev.core.stat.modifier.ModifierBucket;

/**
 * A class representing a resource of an entity <br>
 * Important: Modifying this class's {@link ModifierBucket} doesn't change its
 * stats, since they are derived from other combat stats
 */
public class ResourceStat extends Stat {

	/**
	 * When a StatEngine is attached (via {@link StatManager#setStatEngine}), the
	 * resource's cap AND regen rate are recomputed from the engine's aggregated
	 * modifiers of the corresponding max/regen stats (e.g. item Health/Mana
	 * bonuses), so the pool can actually reach its boosted max.
	 */
	private static final Map<String, StatType> MAX_STAT_BY_NAME = Map.of(
			StatType.HEALTH_RESOURCE.name(), StatType.HEALTH_MAX,
			StatType.MANA_RESOURCE.name(), StatType.MANA_MAX);

	private static final Map<String, StatType> REGEN_STAT_BY_NAME = Map.of(
			StatType.HEALTH_RESOURCE.name(), StatType.HEALTH_REGEN,
			StatType.MANA_RESOURCE.name(), StatType.MANA_REGEN);

	private final LongFunction<Double> regenSupplier; // base regen per second
	private final LongFunction<Double> maxSupplier; // base max value
	private long lastTick;
	private StatEngine statEngine;

	public ResourceStat(String name, LongFunction<Double> maxSupplier, LongFunction<Double> regenSupplier, long now) {
		super(name, maxSupplier.apply(now), maxSupplier.apply(now));
		this.maxSupplier = maxSupplier;
		this.regenSupplier = regenSupplier;
		this.lastTick = now;
	}

	public void setStatEngine(StatEngine statEngine) {
		this.statEngine = statEngine;
	}

	@Override
	public synchronized double getMax(long now) {
		double max = maxSupplier.apply(now);
		if (statEngine != null && max >= 0) {
			StatType maxStat = MAX_STAT_BY_NAME.get(getName());
			if (maxStat != null) {
				max = statEngine.computeValue(StatTypeAdapter.toId(maxStat), max);
			}
		}
		return max;
	}

	@Override
	public synchronized double getCurrent(long now) {
		double max = getMax(now);
		return max < 0 ? current : Math.min(current, max);
	}

	@Override
	public void setCurrent(double value) {
		double max = getMax(System.currentTimeMillis());
		this.current = max < 0 ? value : Math.max(0, Math.min(value, max));
	}

	public synchronized void tick(long now) {
		if (lastTick == now) {
			return;
		}

		double elapsedSeconds = (now - lastTick) / 1000.0;

		// Calculate regeneration (convert from per 5 seconds to per second).
		// Engine modifiers (e.g. item Mana/Health Regen) boost the rate too.
		double effectiveRegen = regenSupplier.apply(now);
		StatType regenStat = REGEN_STAT_BY_NAME.get(getName());
		if (statEngine != null && regenStat != null) {
			effectiveRegen = statEngine.computeValue(StatTypeAdapter.toId(regenStat), effectiveRegen);
		}
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
