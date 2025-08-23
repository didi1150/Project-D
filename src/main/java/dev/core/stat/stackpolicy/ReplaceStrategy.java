package dev.core.stat.stackpolicy;

import dev.core.stat.ModifierBucket;
import dev.core.stat.StatModifier;

public class ReplaceStrategy implements StackingStrategy {

	@Override
	public void apply(ModifierBucket bucket, StatModifier modifier) {
		bucket.removeIf(mod -> mod.statType == modifier.statType);
		bucket.add(modifier);
	}

}
