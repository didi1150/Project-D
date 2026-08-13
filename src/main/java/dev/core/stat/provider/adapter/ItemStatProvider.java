package dev.core.stat.provider.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import dev.core.entity.RPGEntity;
import dev.core.item.RPGItem;
import dev.core.stat.adapter.StatTypeAdapter;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.provider.StatProvider;

/**
 * Adapter that wraps an RPGItem and exposes its stats as a StatProvider.
 * Allows items to work with the new StatEngine without refactoring RPGItem.
 */
public class ItemStatProvider implements StatProvider {

    private final RPGItem item;
    private final String providerId;
    private boolean isActive; // true if item is equipped

    public ItemStatProvider(@NotNull RPGItem item, boolean isActive) {
        this.item = item;
        this.providerId = "item:" + item.getId();
        this.isActive = isActive;
    }

    @NotNull
    @Override
    public Map<String, List<StatModifier>> provideModifiers(@NotNull RPGEntity entity) {
        Map<String, List<StatModifier>> result = new HashMap<>();

        List<StatModifier> relevantStats = isActive ? item.getActiveStats() : item.getPassiveStats();

        for (StatModifier mod : relevantStats) {
            String statId = StatTypeAdapter.toId(mod.statType);
            result.computeIfAbsent(statId, k -> new ArrayList<>()).add(mod);
        }

        return result;
    }

    @NotNull
    @Override
    public String getId() {
        return providerId;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public RPGItem getItem() {
        return item;
    }
}
