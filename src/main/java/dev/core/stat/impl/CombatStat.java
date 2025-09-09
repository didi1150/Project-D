package dev.core.stat.impl;

import dev.core.stat.Stat;

public class CombatStat extends Stat {

	public CombatStat(String name, double current) {
		super(name, current, 0);
	}

	@Override
	public double getCurrent(long now) {
		return modifierBucket.getFinalValue(current);
	}

	@Override
	public double getMax(long now) {
		return 0;
	}

}
