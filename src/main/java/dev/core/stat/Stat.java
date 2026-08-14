package dev.core.stat;

import dev.core.stat.modifier.ModifierBucket;
import dev.core.stat.modifier.StatModifier;

public abstract class Stat {

	private final String name;
	protected double current;
	private double max; // -1 to remove cap
	protected ModifierBucket modifierBucket;
	private boolean capped = false;

	public Stat(String name, double current, double max) {
		this.name = name;
		this.current = current;
		this.max = max;
		this.capped = max == -1;
		this.modifierBucket = new ModifierBucket();
	}

	public String getName() {
		return name;
	}

	public abstract double getMax(long now);

	public abstract double getCurrent(long now);

	/**
	 * The raw base value of this stat, WITHOUT any modifier bucket applied.
	 * The StatEngine aggregates modifiers itself (via providers such as
	 * {@code ModifierBucketProvider}), so callers feeding a value into the
	 * engine must use this instead of {@link #getCurrent(long)} to avoid
	 * applying bucket modifiers twice. Defaults to {@link #getCurrent(long)}
	 * for stats that do not apply their bucket (e.g. resources).
	 */
	public double getBaseValue(long now) {
		return getCurrent(now);
	}

	protected void setCurrent(double value) {
		this.current = capped ? Math.max(0, Math.min(value, max)) : value;
	}

	public void modify(double delta) {
		setCurrent(current + delta);
	}

	public void addModifier(StatModifier statModifier) {
		modifierBucket.add(statModifier);
	}

	public void removeModifier(StatModifier statModifier) {
		modifierBucket.remove(statModifier);
	}

	/**
	 * Return active (non-expired) modifiers for this stat at time 'now'.
	 * Migration helper for the new StatEngine.
	 */
	public java.util.List<StatModifier> getActiveModifiers(long now) {
		if (modifierBucket == null)
			return java.util.List.of();
		return modifierBucket.active(now);
	}

}
