package dev.core.stat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ModifierBucket {

	private final List<StatModifier> modifiers;

	private boolean dirty = true;
	private double cachedValue = Double.NaN;
	private double lastCachedBaseValue = Double.NaN;

	public ModifierBucket() {
		this.modifiers = new ArrayList<StatModifier>();
	}

	public boolean removeIf(Predicate<? super StatModifier> predicate) {
		return modifiers.removeIf(predicate);
	}

	public void addModifier(StatModifier modifier) {
		modifier.stackPolicy.apply(this, modifier);
	}

	public void add(StatModifier modifier) {
		modifiers.add(modifier);
		dirty = true;
	}

	public void removeExpired(long now) {
		if (modifiers.removeIf(mod -> mod.expired(now))) {
			dirty = true;
		}
	}

	public List<StatModifier> active(long now) {
		return modifiers.stream().filter(m -> !m.expired(now)).toList();
	}

	public double getFinalValue(double baseValue, long gameTime) {

		if (cachedValue != Double.NaN && !dirty && lastCachedBaseValue == baseValue) {
			return cachedValue;
		}
		lastCachedBaseValue = baseValue;
		modifiers.removeIf(m -> m.expired(gameTime));

		Map<ModifierType, List<StatModifier>> grouped = modifiers.stream()
				.collect(Collectors.groupingBy(s -> s.modifierType));
		cachedValue = baseValue;

		cachedValue += flatLayer(grouped.get(ModifierType.FLAT));
		cachedValue *= percentAddLayer(grouped.get(ModifierType.PERCENT_ADD));
		cachedValue *= percentMulLayer(grouped.get(ModifierType.MULTIPLY));

		cachedValue = overrideLayer(grouped.get(ModifierType.OVERRIDE), cachedValue);
		dirty = false;
		return cachedValue;
	}

	private double flatLayer(List<StatModifier> grouped) {
		if (grouped == null || grouped.isEmpty()) {
			return 0;
		}
		return grouped.stream().mapToDouble(s -> s.amount).sum();
	}

	private double percentAddLayer(List<StatModifier> grouped) {
		if (grouped == null || grouped.isEmpty()) {
			return 1;
		}
		return 1 + grouped.stream().mapToDouble(s -> s.amount).sum();
	}

	private double percentMulLayer(List<StatModifier> grouped) {
		if (grouped == null || grouped.isEmpty()) {
			return 1;
		}
		return grouped.stream().mapToDouble(m -> 1 + m.amount).reduce(1, (a, b) -> a * b);
	}

	private double overrideLayer(List<StatModifier> grouped, double current) {
		if (grouped == null || grouped.isEmpty()) {
			return current;
		}
		return grouped.stream().max(Comparator.comparingLong(s -> s.appliedAt)).map(s -> s.amount).orElse(current);
	}

	public void clear() {
		modifiers.clear();
		dirty = true;
	}

	public void markDirty() {
		dirty = true;
	}

	public int size() {
		return modifiers.size();
	}

	public StatModifier get(int index) {
		return modifiers.get(index);
	}

	public StatModifier remove(int index) {
		dirty = true;
		return modifiers.remove(index);
	}
}
