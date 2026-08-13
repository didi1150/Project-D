package dev.core.stat.modifier;

import dev.core.stat.StatTarget;
import dev.core.stat.StatType;

public class StatModifier {

	public final double amount;
	public final ModifierStackPolicy stackPolicy;
	public final StatModifierType statModifierType;
	public final StatType statType;
	public final String sourceId;
	public final double duration; // seconds; <= 0 means permanent
	public final long appliedAt; // In Minecraft Ticks 1/20s = 50ms
	public final StatTarget statTarget;
	/** Priority for ordering modifiers. Higher number = higher priority. Default 0. */
	public final int priority;

	private final long expireTime;

	public StatModifier(double amount, ModifierStackPolicy stackPolicy, StatModifierType statModifierType, StatType statType,
			String sourceId, double duration, long appliedAt, StatTarget statTarget, int priority) {
		this.amount = amount;
		this.stackPolicy = stackPolicy;
		this.statModifierType = statModifierType;
		this.statType = statType;
		this.sourceId = sourceId;
		this.duration = duration;
		this.appliedAt = appliedAt;
		this.statTarget = statTarget;
		this.priority = priority;
		this.expireTime = appliedAt + (long) duration * 1000;
	}

	public StatModifier(double amount, StatModifierType statModifierType, StatType statType, String sourceId, long appliedAt) {
		this(amount, statModifierType, statType, sourceId, appliedAt, 0);
	}

	/**
	 * Create a modifier with explicit priority.
	 */
	public StatModifier(double amount, StatModifierType statModifierType, StatType statType, String sourceId, long appliedAt, int priority) {
		this(amount, ModifierStackPolicy.STACK, statModifierType, statType, sourceId, -1, appliedAt, StatTarget.BOTH, priority);
	}

	public boolean expired(long now) {
		return duration > 0 && now >= expireTime;
	}

	/**
	 * Builder for creating StatModifier instances with all options.
	 */
	public static Builder builder(double amount, StatModifierType type, StatType statType, String sourceId) {
		return new Builder(amount, type, statType, sourceId);
	}

	public static class Builder {
		private final double amount;
		private final StatModifierType statModifierType;
		private final StatType statType;
		private final String sourceId;
		private ModifierStackPolicy stackPolicy = ModifierStackPolicy.STACK;
		private double duration = -1;
		private long appliedAt = System.currentTimeMillis();
		private StatTarget statTarget = StatTarget.BOTH;
		private int priority = 0;

		public Builder(double amount, StatModifierType statModifierType, StatType statType, String sourceId) {
			this.amount = amount;
			this.statModifierType = statModifierType;
			this.statType = statType;
			this.sourceId = sourceId;
		}

		public Builder stackPolicy(ModifierStackPolicy stackPolicy) {
			this.stackPolicy = stackPolicy;
			return this;
		}

		public Builder duration(double duration) {
			this.duration = duration;
			return this;
		}

		public Builder appliedAt(long appliedAt) {
			this.appliedAt = appliedAt;
			return this;
		}

		public Builder statTarget(StatTarget statTarget) {
			this.statTarget = statTarget;
			return this;
		}

		public Builder priority(int priority) {
			this.priority = priority;
			return this;
		}

		public StatModifier build() {
			return new StatModifier(amount, stackPolicy, statModifierType, statType, sourceId, duration, appliedAt, statTarget, priority);
		}
	}
}
