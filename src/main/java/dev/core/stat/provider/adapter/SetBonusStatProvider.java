package dev.core.stat.provider.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import dev.core.entity.RPGEntity;
import dev.core.stat.adapter.StatTypeAdapter;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.provider.StatProvider;

/**
 * Exposes a set bonus's stat modifiers to the StatEngine. Set bonuses register
 * a provider (rather than pushing into the legacy StatManager buckets) so the
 * modifiers are applied exactly once.
 */
public class SetBonusStatProvider implements StatProvider {

    private final String id;
    private final List<StatModifier> modifiers;

    public SetBonusStatProvider(String id, List<StatModifier> modifiers) {
        this.id = id;
        this.modifiers = new ArrayList<>(modifiers);
    }

    @NotNull
    @Override
    public Map<String, List<StatModifier>> provideModifiers(@NotNull RPGEntity entity) {
        Map<String, List<StatModifier>> result = new HashMap<>();
        for (StatModifier mod : modifiers) {
            result.computeIfAbsent(StatTypeAdapter.toId(mod.statType), k -> new ArrayList<>()).add(mod);
        }
        return result;
    }

    @NotNull
    @Override
    public String getId() {
        return id;
    }
}
