package dev.core.stat.stackpolicy;

import dev.core.stat.ModifierBucket;
import dev.core.stat.StatModifier;

public class StackStrategy implements StackingStrategy {

	@Override
	public void apply(ModifierBucket bucket, StatModifier modifier) {
		bucket.add(modifier);
	}

}
