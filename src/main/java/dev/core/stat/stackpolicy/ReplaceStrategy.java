package dev.core.stat.stackpolicy;

import dev.core.stat.modifier.ModifierBucket;
import dev.core.stat.modifier.StatModifier;

public class ReplaceStrategy implements StackingStrategy {

	@Override
	public void apply(ModifierBucket bucket, StatModifier modifier) {
		bucket.removeIf(mod -> mod.statType == modifier.statType);
		bucket.add(modifier);
	}

}
