package dev.core.stat.stackpolicy;

import dev.core.stat.modifier.ModifierBucket;
import dev.core.stat.modifier.ModifierStackPolicy;
import dev.core.stat.modifier.StatModifier;

public class UniqueBySourceStrategy implements StackingStrategy {

	@Override
	public void apply(ModifierBucket bucket, StatModifier modifier) {
		if (modifier.stackPolicy == ModifierStackPolicy.UNIQUE_BY_SOURCE) {
			bucket.removeIf(mod -> mod.sourceId.equals(modifier.sourceId));
			bucket.add(modifier);
		}
	}

}
