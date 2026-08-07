package dev.core.stat.provider;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

import dev.core.entity.RPGEntity;
import dev.core.stat.modifier.StatModifier;

/**
 * Base implementation for stat providers with fluent API.
 * Simplifies common provider patterns and reduces boilerplate.
 */
public abstract class AbstractStatProvider implements StatProvider {

    protected final String id;
    protected final Map<String, Function<RPGEntity, Map<String, java.util.List<StatModifier>>>> modifierFactories = new HashMap<>();

    public AbstractStatProvider(@NotNull String id) {
        this.id = id;
    }

    @Override
    @NotNull
    public String getId() {
        return id;
    }

    @Override
    @NotNull
    public Map<String, java.util.List<StatModifier>> provideModifiers(@NotNull RPGEntity entity) {
        Map<String, java.util.List<StatModifier>> result = new HashMap<>();
        for (Function<RPGEntity, Map<String, java.util.List<StatModifier>>> factory : modifierFactories.values()) {
            Map<String, java.util.List<StatModifier>> mods = factory.apply(entity);
            if (mods != null) {
                for (Map.Entry<String, java.util.List<StatModifier>> entry : mods.entrySet()) {
                    result.computeIfAbsent(entry.getKey(), k -> new java.util.ArrayList<>()).addAll(entry.getValue());
                }
            }
        }
        return result;
    }

    /**
     * Register a modifier factory for this provider.
     * Useful for dynamic modifier generation based on entity state.
     */
    public AbstractStatProvider registerModifierFactory(@NotNull String key,
            Function<RPGEntity, Map<String, java.util.List<StatModifier>>> factory) {
        modifierFactories.put(key, factory);
        return this;
    }

    @Override
    public void onAttach(@NotNull RPGEntity entity) {
        // Override in subclasses if needed
    }

    @Override
    public void onDetach(@NotNull RPGEntity entity) {
        // Override in subclasses if needed
    }

    @Override
    public boolean isDynamic() {
        return false;
    }
}
