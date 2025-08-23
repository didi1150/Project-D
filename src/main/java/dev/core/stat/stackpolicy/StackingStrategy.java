package dev.core.stat.stackpolicy;

import dev.core.stat.ModifierBucket;
import dev.core.stat.StatModifier;

public interface StackingStrategy {
	void apply(ModifierBucket bucket, StatModifier modifier);
}
