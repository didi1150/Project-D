package dev.core.stat.provider.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.core.entity.RPGEntity;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.adapter.StatTypeAdapter;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.provider.StatProvider;

/**
 * Provider that exposes all modifiers currently stored in the entity's StatManager
 * (the ModifierBuckets). This allows the new StatEngine to see legacy modifiers
 * without rewriting existing code immediately.
 */
public class ModifierBucketProvider implements StatProvider {

    private final String id;
    private final StatManager statManager;

    public ModifierBucketProvider(RPGEntity owner) {
        this.id = "statmanager:" + owner.getUuid().toString();
        this.statManager = owner.getStatManager();
    }

    @Override
    public Map<String, List<StatModifier>> provideModifiers(RPGEntity entity) {
        Map<String, List<StatModifier>> map = new HashMap<>();
        long now = System.currentTimeMillis();
        for (var entry : statManager.getStats().entrySet()) {
            StatType type = entry.getKey();
            var stat = entry.getValue();
            List<StatModifier> active = stat.getActiveModifiers(now);
            if (active == null || active.isEmpty())
                continue;
            String id = StatTypeAdapter.toId(type);
            map.put(id, new ArrayList<>(active));
        }
        return map;
    }

    @Override
    public String getId() {
        return id;
    }

}
