package dev.core.stat.modifier;

import dev.core.stat.stackpolicy.MaxOnlyStrategy;
import dev.core.stat.stackpolicy.MinOnlyStrategy;
import dev.core.stat.stackpolicy.ReplaceStrategy;
import dev.core.stat.stackpolicy.StackStrategy;
import dev.core.stat.stackpolicy.StackingStrategy;
import dev.core.stat.stackpolicy.UniqueBySourceStrategy;

public enum ModifierStackPolicy {

	STACK(new StackStrategy()), UNIQUE_BY_SOURCE(new UniqueBySourceStrategy()), // Only one unique per source id
	REPLACE(new ReplaceStrategy()), // Replace the old stattype modifier, regardless of source id
	MAX_ONLY(new MaxOnlyStrategy()), MIN_ONLY(new MinOnlyStrategy());

	private final StackingStrategy strategy;

	private ModifierStackPolicy(StackingStrategy strategy) {
		this.strategy = strategy;
	}

	public void apply(ModifierBucket bucket, StatModifier modifier) {
		this.strategy.apply(bucket, modifier);
	}

}
