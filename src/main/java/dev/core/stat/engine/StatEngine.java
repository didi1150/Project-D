package dev.core.stat.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import dev.core.entity.RPGEntity;
import dev.core.stat.modifier.StatModifierType;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.provider.StatProvider;

/**
 * Aggregates modifiers from all providers and computes final stat values.
 * Handles deterministic stacking rules and caching.
 */
public class StatEngine {

    private final RPGEntity entity;
    private final Map<String, StatProvider> providers = new HashMap<>();
    private final Map<String, List<StatModifier>> cachedModifiers = new HashMap<>();
    private boolean dirty = true;

    public StatEngine(@NotNull RPGEntity entity) {
        this.entity = entity;
    }

    /**
     * Register a stat provider. Invalidates cache and calls onAttach.
     */
    public void registerProvider(@NotNull StatProvider provider) {
        if (providers.containsKey(provider.getId())) {
            throw new IllegalArgumentException("Provider '" + provider.getId() + "' already registered");
        }
        providers.put(provider.getId(), provider);
        provider.onAttach(entity);
        invalidate();
    }

    /**
     * Unregister a stat provider. Invalidates cache and calls onDetach.
     */
    public void unregisterProvider(@NotNull String providerId) {
        StatProvider provider = providers.remove(providerId);
        if (provider != null) {
            provider.onDetach(entity);
            invalidate();
        }
    }

    /**
     * Mark cache as dirty; will recompute on next aggregation.
     */
    public void invalidate() {
        dirty = true;
        cachedModifiers.clear();
    }

    /**
     * Get all modifiers for a stat id, aggregating from all providers. Results are
     * cached until invalidate() is called.
     */
    public List<StatModifier> getModifiers(@NotNull String statId) {
        ensureComputed();
        return new ArrayList<>(cachedModifiers.getOrDefault(statId, new ArrayList<>()));
    }

    /**
     * Compute final value of a stat after applying all modifiers. Uses
     * deterministic stacking: BASE -> ADD -> PERCENT_ADD -> MULTIPLY -> OVERRIDE.
     */
    public double computeValue(@NotNull String statId, double baseValue) {
        List<StatModifier> modifiers = getModifiers(statId);
        return applyModifiers(baseValue, modifiers);
    }

    /**
     * Recompute cached modifiers from all providers.
     */
    private void ensureComputed() {
        if (!dirty) {
            return;
        }

        cachedModifiers.clear();

        for (StatProvider provider : providers.values()) {
            Map<String, List<StatModifier>> contributed = provider.provideModifiers(entity);
            for (Map.Entry<String, List<StatModifier>> entry : contributed.entrySet()) {
                String statId = entry.getKey();
                List<StatModifier> mods = entry.getValue();
                cachedModifiers.computeIfAbsent(statId, k -> new ArrayList<>()).addAll(mods);
            }
        }

        dirty = false;
    }

    /**
     * Apply modifiers in deterministic order: 1. Sum all ADD modifiers (sorted by
     * priority) 2. Sum all PERCENT_ADD modifiers (sorted by priority) 3. Apply all
     * MULTIPLY modifiers sequentially (sorted by priority) 4. Apply
     * highest-priority OVERRIDE (or last if tied)
     */
    private double applyModifiers(double base, List<StatModifier> modifiers) {
        Map<StatModifierType, List<StatModifier>> grouped = groupModifiersByType(modifiers);
        double result = base;

        // Step 1: Apply flat adds
        result = applyOperation(grouped.get(StatModifierType.FLAT), result, 
            (sum, mod) -> sum + mod.amount);

        // Step 2: Apply percent adds
        result = applyOperation(grouped.get(StatModifierType.PERCENT_ADD), result,
            (sum, mod) -> sum + base * (mod.amount / 100.0));

        // Step 3: Apply multiplies
        result = applyOperation(grouped.get(StatModifierType.MULTIPLY), result,
            (product, mod) -> product * mod.amount);

        // Step 4: Apply override (highest priority wins)
        List<StatModifier> overrides = grouped.get(StatModifierType.OVERRIDE);
        if (overrides != null && !overrides.isEmpty()) {
            result = overrides.get(0).amount;
        }

        return Math.max(0, result); // ensure non-negative
    }

    /**
     * Group modifiers by their type and sort appropriately.
     */
    private Map<StatModifierType, List<StatModifier>> groupModifiersByType(List<StatModifier> modifiers) {
        Map<StatModifierType, List<StatModifier>> grouped = new HashMap<>();
        for (StatModifierType type : StatModifierType.values()) {
            grouped.put(type, new ArrayList<>());
        }

        for (StatModifier mod : modifiers) {
            grouped.get(mod.statModifierType).add(mod);
        }

        // Sort ascending by priority (lower applies first)
        for (StatModifierType type : new StatModifierType[]{StatModifierType.FLAT, StatModifierType.PERCENT_ADD, StatModifierType.MULTIPLY}) {
            grouped.get(type).sort((a, b) -> Integer.compare(getPriority(a), getPriority(b)));
        }

        // Sort descending for overrides (higher priority wins)
        grouped.get(StatModifierType.OVERRIDE).sort((a, b) -> Integer.compare(getPriority(b), getPriority(a)));

        return grouped;
    }

    /**
     * Apply a modifier operation (sum, multiply, etc.) with proper sorting.
     */
    @FunctionalInterface
    private interface ModifierOperation {
        double apply(double current, StatModifier modifier);
    }

    private double applyOperation(List<StatModifier> mods, double current, ModifierOperation op) {
        if (mods == null) {
            return current;
        }
        double result = current;
        for (StatModifier mod : mods) {
            result = op.apply(result, mod);
        }
        return result;
    }

    /**
     * Extract priority from a modifier. Default 0 if not set. (You may enhance
     * StatModifier to include a priority field)
     */
    private int getPriority(@NotNull StatModifier mod) {
        try {
            return mod.priority;
        } catch (Throwable t) {
            return 0;
        }
    }

    public Map<String, StatProvider> getProviders() {
        return new HashMap<>(providers);
    }

    public int providerCount() {
        return providers.size();
    }
}
