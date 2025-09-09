package dev.core.stat.stackpolicy;

import dev.core.stat.modifier.ModifierBucket;
import dev.core.stat.modifier.StatModifier;

public class StackStrategy implements StackingStrategy {

	@Override
	public void apply(ModifierBucket bucket, StatModifier modifier) {
		bucket.add(modifier);
	}

}
