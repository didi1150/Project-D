package dev.core.stat;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class StatManager {

	private final Map<StatType, Stat> stats;

	public StatManager(Map<StatType, Stat> preStats) {
		this.stats = new HashMap<StatType, Stat>();

		for (Entry<StatType, Stat> entry : preStats.entrySet()) {
			stats.put(entry.getKey(), entry.getValue());
		}
	}

	public void addStatModifier(StatModifier statModifier) {
		stats.get(statModifier.statType).addModifier(statModifier);
	}

	public void removeStatModifier(StatModifier statModifier) {
		stats.get(statModifier.statType).removeModifier(statModifier);
	}

	public void tick(long now) {
		for (Stat stat : stats.values()) {
			stat.modifierBucket.removeExpired(now);
			if (stat instanceof ResourceStat rs) {
				rs.tick(now);
			}
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

	public void setCurrentValue(StatType type, double value) {
		Stat stat = stats.get(type);
		stat.setCurrent(value);
	}

	public void modifyStat(StatType type, double delta) {
		stats.get(type).modify(delta);
	}

}
