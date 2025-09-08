package dev.bukkit.storage.progression;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.storage.database.PlayerClassProgression;
import dev.core.storage.database.ProgressionCacheStrategy;

public class HashMapProgressionCache implements ProgressionCacheStrategy {

    private final Map<UUID, Map<RPGClassType, PlayerClassProgression>> cache = new HashMap<>();
    private final Map<UUID, RPGClassType> activeClasses = new HashMap<>();

    @Override
    public Optional<PlayerClassProgression> get(UUID playerId, RPGClassType type) {
        return Optional.ofNullable(cache.getOrDefault(playerId, Collections.emptyMap()).get(type));
    }

    @Override
    public Map<RPGClassType, PlayerClassProgression> getAll(UUID playerId) {
        return cache.getOrDefault(playerId, Collections.emptyMap());
    }

    @Override
    public void put(UUID playerId, PlayerClassProgression progression) {
        cache.computeIfAbsent(playerId, id -> new HashMap<>()).put(progression.getClassType(), progression);
    }

    @Override
    public void remove(UUID playerId, RPGClassType type) {
        cache.getOrDefault(playerId, Collections.emptyMap()).remove(type);
    }

    @Override
    public void clear(UUID playerId) {
        cache.remove(playerId);
    }

    @Override
    public RPGClassType getActiveClass(UUID playerId) {
        return activeClasses.getOrDefault(playerId, RPGClassType.NONE);
    }

    @Override
    public void setActiveClass(UUID playerID, RPGClassType rpgClassType) {
        activeClasses.put(playerID, rpgClassType);
    }

}
