package dev.core.entity.boss;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of boss definitions loaded from config, organized per floor. The
 * first definition registered for a floor is the floor's boss.
 */
public class BossDefinitionRegistry {

    private static BossDefinitionRegistry instance;

    private final Map<Integer, List<BossDefinition>> byFloor = new HashMap<>();
    private final Map<Integer, Map<String, BossDefinition>> byFloorAndId = new HashMap<>();

    public static BossDefinitionRegistry getInstance() {
        if (instance == null) {
            instance = new BossDefinitionRegistry();
        }
        return instance;
    }

    public synchronized void registerAll(Collection<BossDefinition> definitions) {
        for (BossDefinition definition : definitions) {
            byFloor.computeIfAbsent(definition.getFloor(), floor -> new ArrayList<>()).add(definition);
            byFloorAndId.computeIfAbsent(definition.getFloor(), floor -> new LinkedHashMap<>())
                    .put(definition.getId(), definition);
        }
    }

    public synchronized void clear() {
        byFloor.clear();
        byFloorAndId.clear();
    }

    public Optional<BossDefinition> getForFloor(int floor) {
        List<BossDefinition> definitions = byFloor.get(floor);
        return definitions == null || definitions.isEmpty() ? Optional.empty()
                : Optional.of(definitions.get(0));
    }

    public Optional<BossDefinition> get(int floor, String id) {
        Map<String, BossDefinition> definitions = byFloorAndId.get(floor);
        return definitions == null ? Optional.empty() : Optional.ofNullable(definitions.get(id));
    }

    public List<BossDefinition> getAllForFloor(int floor) {
        return Collections.unmodifiableList(byFloor.getOrDefault(floor, List.of()));
    }
}
