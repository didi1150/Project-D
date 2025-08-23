package dev.core.stat;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import dev.core.entity.RPGEntity;

public class StatManager {

	private final Map<StatType, Stat> stats;
	private RPGEntity entity;

	public StatManager(Map<StatType, Stat> preStats, RPGEntity entity) {
		this.entity = entity;
		this.stats = new HashMap<StatType, Stat>();

		for (Entry<StatType, Stat> entry : preStats.entrySet()) {
			stats.put(entry.getKey(), entry.getValue());
		}
	}

	public void addStatModifier(StatModifier statModifier) {
		stats.get(statModifier.statType).addModifier(statModifier);
	}

	public void tick(long now) {
		for (Stat stat : stats.values()) {
			stat.modifierBucket.removeExpired(now);
		}
	}

	public double getCurrentValue(StatType type, long now) {
		Stat stat = stats.get(type);
		if (stat == null) {
			return 0;
		}
		return stat.getCurrent(now);
	}

	public double getMaxValue(StatType type, long now) {
		Stat stat = stats.get(type);
		return stat.getMax(now);
	}

}
