package dev.core.stat.modifier;

import dev.core.stat.StatTarget;
import dev.core.stat.StatType;

public class StatModifier {

	public final double amount;
	public final ModifierStackPolicy stackPolicy;

	public final ModifierType modifierType;
	public final StatType statType;
	public final String sourceId;
	public final double duration; // seconds; <= 0 means permanent
	public final long appliedAt; // In Minecraft Ticks 1/20s = 50ms
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
		this.expireTime = appliedAt + (long) duration * 1000;
	}

	public StatModifier(double amount, ModifierType modifierType, StatType statType, String sourceId, long appliedAt) {
		this.amount = amount;
		this.modifierType = modifierType;
		this.statType = statType;
		this.sourceId = sourceId;
		this.appliedAt = appliedAt;
		this.duration = -1;
		this.statTarget = StatTarget.BOTH;
		this.stackPolicy = ModifierStackPolicy.STACK;
		this.expireTime = 0;
	}

	public boolean expired(long now) {
		return duration > 0 && now >= expireTime;
	}
}
