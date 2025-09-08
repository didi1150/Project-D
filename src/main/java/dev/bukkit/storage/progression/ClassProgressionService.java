package dev.bukkit.storage.progression;

import java.util.Map;
import java.util.UUID;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.storage.database.PlayerClassProgression;
import dev.core.storage.database.PlayerProgression;
import dev.core.storage.database.ProgressionCacheStrategy;
import dev.core.storage.database.ProgressionDatabaseStrategy;

public class ClassProgressionService {

    private final ProgressionCacheStrategy cache;
    private final ProgressionDatabaseStrategy database;

    public ClassProgressionService(ProgressionCacheStrategy cache, ProgressionDatabaseStrategy database) {
        this.cache = cache;
        this.database = database;
    }

    public PlayerClassProgression getProgression(UUID playerId, RPGClassType type) {
        return cache.get(playerId, type).orElseGet(() -> {
            // Load from DB if missing in cache
            Map<RPGClassType, PlayerClassProgression> all = database.loadAll(playerId);
            for (PlayerClassProgression prog : all.values()) {
                cache.put(playerId, prog);
            }
            return all.getOrDefault(type, new PlayerClassProgression(type));
        });
    }

    public void addExperience(UUID playerId, RPGClassType type, int xp) {
        PlayerClassProgression prog = getProgression(playerId, type);
        prog.addXp(xp);
        cache.put(playerId, prog);
        database.save(playerId, prog);
    }

    public void saveClassProgression(UUID playerId, PlayerClassProgression playerClassProgression) {
        cache.put(playerId, playerClassProgression);
        database.save(playerId, playerClassProgression);
    }

    public void saveAll(UUID playerId) {
        database.saveAll(playerId, cache.getAll(playerId));
        database.setActiveClass(playerId, cache.getActiveClass(playerId));
    }

    public void setActiveClass(PlayerProgression playerProgression) {
        cache.setActiveClass(playerProgression.getPlayerId(), playerProgression.getActiveClass());
        database.setActiveClass(playerProgression.getPlayerId(), playerProgression.getActiveClass());
    }

    public RPGClassType getActiveClass(UUID playerId) {
        RPGClassType cachedActiveClassType = cache.getActiveClass(playerId);
        if (cachedActiveClassType == RPGClassType.NONE) {
            cache.setActiveClass(playerId, database.getActiveClass(playerId));
        }
        return cache.getActiveClass(playerId);
    }

}
