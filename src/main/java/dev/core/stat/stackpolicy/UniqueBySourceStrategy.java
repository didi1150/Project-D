package dev.core.stat.stackpolicy;

import dev.core.stat.ModifierBucket;
import dev.core.stat.ModifierStackPolicy;
import dev.core.stat.StatModifier;

public class UniqueBySourceStrategy implements StackingStrategy {

	@Override
	public void apply(ModifierBucket bucket, StatModifier modifier) {
		if (modifier.stackPolicy == ModifierStackPolicy.UNIQUE_BY_SOURCE) {
			bucket.removeIf(mod -> mod.sourceId.equals(modifier.sourceId));
			bucket.add(modifier);
		}
	}

}
