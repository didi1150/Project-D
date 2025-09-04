package dev.core.stat;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ModifierBucket {

	private final List<StatModifier> modifiers;

	private boolean dirty = true;
	private double cachedValue = Double.NaN;
	private double lastCachedBaseValue = Double.NaN;

	public ModifierBucket() {
		this.modifiers = new ArrayList<>();
	}

	// --- Mutations mark dirty ---

	public boolean removeIf(Predicate<? super StatModifier> predicate) {
		boolean removed = modifiers.removeIf(predicate);
		if (removed)
			dirty = true;
		return removed;
	}

	public void addModifier(StatModifier modifier) {
		modifier.stackPolicy.apply(this, modifier);
		dirty = true;
	}

	public void add(StatModifier modifier) {
		modifiers.add(modifier);
		dirty = true;
	}

	public void remove(StatModifier statModifier) {
		if (modifiers.remove(statModifier)) {
			dirty = true;
		}
	}

	public void clear() {
		modifiers.clear();
		dirty = true;
	}

	public StatModifier remove(int index) {
		dirty = true;
		return modifiers.remove(index);
	}

	// --- Expiry ---

	/**
	 * Should be called periodically (e.g. once per tick) outside of getFinalValue.
	 */
	public void removeExpired(long now) {
		if (modifiers.removeIf(mod -> mod.expired(now))) {
			dirty = true;
		}
	}

	// --- Main value calculation ---

	public double getFinalValue(double baseValue) {
		// Return cached if still valid
		if (!Double.isNaN(cachedValue) && !dirty && lastCachedBaseValue == baseValue) {
			return cachedValue;
		}

		// Otherwise, recalc
		lastCachedBaseValue = baseValue;

		Map<ModifierType, List<StatModifier>> grouped = modifiers.stream()
				.collect(Collectors.groupingBy(s -> s.modifierType));

		double result = baseValue;
		result += flatLayer(grouped.get(ModifierType.FLAT));
		result *= percentAddLayer(grouped.get(ModifierType.PERCENT_ADD));
		result *= percentMulLayer(grouped.get(ModifierType.MULTIPLY));
		result = overrideLayer(grouped.get(ModifierType.OVERRIDE), result);

		cachedValue = result;
		dirty = false;
		return cachedValue;
	}

	// --- Layers ---

	private double flatLayer(List<StatModifier> grouped) {
		if (grouped == null || grouped.isEmpty())
			return 0;
		return grouped.stream().mapToDouble(s -> s.amount).sum();
	}

	private double percentAddLayer(List<StatModifier> grouped) {
		if (grouped == null || grouped.isEmpty())
			return 1;
		return 1 + grouped.stream().mapToDouble(s -> s.amount).sum();
	}

	private double percentMulLayer(List<StatModifier> grouped) {
		if (grouped == null || grouped.isEmpty())
			return 1;
		return grouped.stream().mapToDouble(m -> 1 + m.amount).reduce(1, (a, b) -> a * b);
	}

	private double overrideLayer(List<StatModifier> grouped, double current) {
		if (grouped == null || grouped.isEmpty())
			return current;
		return grouped.stream().max(Comparator.comparingLong(s -> s.appliedAt)).map(s -> s.amount).orElse(current);
	}

	// --- Utilities ---

	public List<StatModifier> active(long now) {
		return modifiers.stream().filter(m -> !m.expired(now)).toList();
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
}
