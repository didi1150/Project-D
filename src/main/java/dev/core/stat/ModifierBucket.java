package dev.core.stat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModifierBucket {

	private final List<StatModifier> modifiers;

	private boolean dirty = true;
	private double cachedValue = Double.NaN;

	public ModifierBucket() {
		this.modifiers = new ArrayList<StatModifier>();
	}

	public void addModifier(StatModifier modifier) {
		if (modifier.stackPolicy == ModifierStackPolicy.UNIQUE_BY_SOURCE) {
			modifiers.removeIf(mod -> mod.sourceId.equals(modifier.sourceId) && mod.statType == modifier.statType);
		}

		else if (modifier.stackPolicy == ModifierStackPolicy.REPLACE) {
			modifiers.removeIf(mod -> mod.statType == modifier.statType);
		}

		else if (modifier.stackPolicy == ModifierStackPolicy.MAX_ONLY) {
			modifiers.removeIf(mod -> mod.sourceId.equals(modifier.sourceId) && mod.statType == modifier.statType
					&& mod.amount < modifier.amount);
		}

		else if (modifier.stackPolicy == ModifierStackPolicy.MAX_ONLY) {
			modifiers.removeIf(mod -> mod.sourceId.equals(modifier.sourceId) && mod.statType == modifier.statType
					&& mod.amount < modifier.amount);
		}

		else if (modifier.stackPolicy == ModifierStackPolicy.MIN_ONLY) {
			modifiers.removeIf(mod -> mod.sourceId.equals(modifier.sourceId) && mod.statType == modifier.statType
					&& mod.amount > modifier.amount);
		} else {
			for (int i = 0; i < modifiers.size(); i++) {
				StatModifier mod = modifiers.get(i);
				if (mod.sourceId.equals(modifier.sourceId) && mod.statType == modifier.statType) {
					int stacks = mod.getCurrentStacks();
					modifier.applyStack(stacks);
					modifiers.set(i, modifier);
					break;
				}
			}
		}
		dirty = true;
	}

	public void removeExpired(double now) {
		if (modifiers.removeIf(mod -> mod.expired(now))) {
			dirty = true;
		}
	}

	public List<StatModifier> active(double now) {
		return modifiers.stream().filter(m -> !m.expired(now)).toList();
	}

	public double getFinalValue(double baseValue, double gameTime) {

		if (cachedValue != Double.NaN && !dirty) {
			return cachedValue;
		}

		modifiers.removeIf(m -> m.expired(gameTime));

		Map<ModifierType, List<StatModifier>> grouped = modifiers.stream()
				.collect(Collectors.groupingBy(s -> s.modifierType));
		cachedValue = baseValue;

		cachedValue += grouped.get(ModifierType.FLAT).stream().mapToDouble(StatModifier::effectiveAmount).sum();
		cachedValue *= (1
				+ grouped.get(ModifierType.PERCENT_ADD).stream().mapToDouble(StatModifier::effectiveAmount).sum());
		cachedValue *= grouped.get(ModifierType.MULTIPLY).stream().mapToDouble(m -> 1 + m.effectiveAmount()).reduce(1,
				(a, b) -> a * b);

		cachedValue = grouped.get(ModifierType.OVERRIDE).stream().mapToDouble(StatModifier::effectiveAmount).sum();
		dirty = false;
		return cachedValue;
	}

}
