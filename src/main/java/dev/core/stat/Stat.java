package dev.core.stat;

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

}
