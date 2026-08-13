package dev.core.stat.stackpolicy;

import dev.core.stat.modifier.ModifierBucket;
import dev.core.stat.modifier.StatModifier;

public class MaxOnlyStrategy implements StackingStrategy {

	@Override
	public void apply(ModifierBucket bucket, StatModifier modifier) {
		boolean replace = true;

		for (int i = bucket.size() - 1; i >= 0; --i) {
			StatModifier mod = bucket.get(i);
			if (mod.statType == modifier.statType && Math.abs(mod.amount) <= Math.abs(modifier.amount)
					&& mod.statModifierType == modifier.statModifierType) {
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
