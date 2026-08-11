package dev.core.entity.boss;

import java.util.List;
import java.util.UUID;

public interface BossEntityContext {
    void broadcast(String message);

    /**
     * Spawns {@code count} entities of the given type near the boss.
     *
     * @return the unique ids of the spawned entities (empty if nothing spawned)
     */
    List<UUID> spawnAdd(String entityType, int count);
}
