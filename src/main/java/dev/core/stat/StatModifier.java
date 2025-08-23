package dev.core.stat;

public class StatModifier {

	public final double amount;
	public final ModifierStackPolicy stackPolicy;

	public final ModifierType modifierType;
	public final StatType statType;
	public final String sourceId;
	public final double duration; // seconds; <= 0 means permanent
	public final long appliedAt; // Use System#currentTimeMillis
	public final StatTarget statTarget;

	private final long expireTime;

	public StatModifier(double amount, ModifierStackPolicy stackPolicy, ModifierType modifierType, StatType statType,
			String sourceId, double duration, long appliedAt, StatTarget statTarget) {
		this.amount = amount;
		this.stackPolicy = stackPolicy;
		this.modifierType = modifierType;
		this.statType = statType;
		this.sourceId = sourceId;
		this.duration = duration;
		this.appliedAt = appliedAt;
		this.statTarget = statTarget;
		this.expireTime = appliedAt + (long) (duration * 1000);
	}

	public boolean expired(long now) {
		return duration > 0 && now >= expireTime;
	}
}
