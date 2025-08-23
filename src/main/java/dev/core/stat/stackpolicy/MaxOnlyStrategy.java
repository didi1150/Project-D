package dev.core.stat.stackpolicy;

import dev.core.stat.ModifierBucket;
import dev.core.stat.StatModifier;

public class MaxOnlyStrategy implements StackingStrategy {

	@Override
	public void apply(ModifierBucket bucket, StatModifier modifier) {
		boolean replace = true;

		for (int i = bucket.size() - 1; i >= 0; --i) {
			StatModifier mod = bucket.get(i);
			if (mod.statType == modifier.statType && Math.abs(mod.amount) <= Math.abs(modifier.amount)
					&& mod.modifierType == modifier.modifierType) {
				bucket.remove(i);
			} else {
				replace = false;
			}
		}
		if (replace) {
			bucket.add(modifier);
		}
	}

}
