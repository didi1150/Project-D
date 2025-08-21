package dev.core.stat;

public class CombatStat extends Stat {

	public CombatStat(String name, double current, double max) {
		super(name, current, max);
	}

	@Override
	public double getCurrent(double now) {
		return modifierBucket.getFinalValue(current, now);
	}

}
