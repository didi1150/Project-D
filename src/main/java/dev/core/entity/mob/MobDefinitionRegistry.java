package dev.core.entity.mob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.core.game.dungeon.proceduralDungeon.util.SpawnTier;

/**
 * Registry of {@link MobDefinition}s loaded from {@code dungeon-mobs.yml},
 * indexed by id and by the spawn tiers each mob may appear in.
 */
public class MobDefinitionRegistry {

    private static MobDefinitionRegistry instance;

    private final Map<String, MobDefinition> byId = new LinkedHashMap<>();
    private final Map<SpawnTier, List<MobDefinition>> byTier = new EnumMap<>(SpawnTier.class);

    public static MobDefinitionRegistry getInstance() {
        if (instance == null) {
            instance = new MobDefinitionRegistry();
        }
        return instance;
    }

    public synchronized void registerAll(Collection<MobDefinition> definitions) {
        for (MobDefinition definition : definitions) {
            byId.put(definition.getId(), definition);
            for (SpawnTier tier : definition.getTiers()) {
                byTier.computeIfAbsent(tier, t -> new ArrayList<>()).add(definition);
            }
        }
    }

    public synchronized void clear() {
        byId.clear();
        byTier.clear();
    }

    public Optional<MobDefinition> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** All mob definitions eligible to spawn in the given tier. */
    public List<MobDefinition> getForTier(SpawnTier tier) {
        return Collections.unmodifiableList(byTier.getOrDefault(tier, List.of()));
    }

    public int size() {
        return byId.size();
    }
}
