package dev.core.stat.stackpolicy;

import dev.core.stat.modifier.ModifierBucket;
import dev.core.stat.modifier.StatModifier;

public interface StackingStrategy {
	void apply(ModifierBucket bucket, StatModifier modifier);
}
