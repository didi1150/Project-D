package dev.core.storage.database;

import java.util.Map;
import java.util.UUID;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.progression.PlayerClassProgression;

public interface ProgressionDatabaseStrategy {

    Map<RPGClassType, PlayerClassProgression> loadAll(UUID playerId);

    RPGClassType getActiveClass(UUID playerId);

    void setActiveClass(UUID playerID, RPGClassType rpgClassType);

    void save(UUID playerId, PlayerClassProgression progression);

    void saveAll(UUID playerId, Map<RPGClassType, PlayerClassProgression> progressions);
}
