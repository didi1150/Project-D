package dev.core.storage.database;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.core.entity.rpgclass.RPGClassType;

public interface ProgressionCacheStrategy {

    Optional<PlayerClassProgression> get(UUID playerId, RPGClassType type);

    Map<RPGClassType, PlayerClassProgression> getAll(UUID playerId);

    RPGClassType getActiveClass(UUID playerId);

    void setActiveClass(UUID playerID, RPGClassType rpgClassType);

    void put(UUID playerId, PlayerClassProgression progression);

    void remove(UUID playerId, RPGClassType type);

    void clear(UUID playerId);

}
