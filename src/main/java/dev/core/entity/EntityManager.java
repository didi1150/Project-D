package dev.core.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EntityManager {

    private final Map<UUID, RPGEntity> entities = new ConcurrentHashMap<>();
    private final Set<UUID> deadEntities = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> spectators = Collections.synchronizedSet(new HashSet<>());

    private static EntityManager instance;

    public static EntityManager getInstance() {
        if (instance == null) {
            instance = new EntityManager();
        }
        return instance;
    }

    public void registerSpectator(UUID uuid) {
        spectators.add(uuid);
    }

    public boolean isSpectator(UUID uuid) {
        return spectators.contains(uuid);
    }

    /**
     * Whether the given entity is a ghost player: either explicitly registered
     * as a spectator (game-state ghosting, e.g. on join) or an RPG player
     * entity that died mid-game (marked dead but kept registered). Ghosts must
     * never be targeted or damaged.
     */
    public boolean isGhost(UUID uuid) {
        if (spectators.contains(uuid)) {
            return true;
        }
        RPGEntity entity = entities.get(uuid);
        return entity != null && entity.getEntityType() == EntityType.PLAYER && deadEntities.contains(uuid);
    }

    public void clearSpectators() {
        spectators.clear();
    }

    /**
     * Register a new entity in the manager. If the entity is already present, it
     * shall be reused
     */
    public void registerEntity(RPGEntity entity) {
        Objects.requireNonNull(entity, "Entity cannot be null");
        if (entities.containsKey(entity.getUuid())) {
            return;
        }
        entities.put(entity.getUuid(), entity);
    }

    /**
     * Mark an entity as dead but still keep it in memory.
     */
    public void markDead(UUID entityId) {
        if (entities.containsKey(entityId)) {
            deadEntities.add(entityId);
        }
    }

    /**
     * Revive an entity (remove from dead list).
     */
    public void revive(UUID entityId) {
        deadEntities.remove(entityId);
    }

    /**
     * Completely remove an entity from the manager.
     */
    public void removeEntity(UUID entityId) {
        entities.remove(entityId);
        deadEntities.remove(entityId);
    }

    /**
     * Get an entity by its ID.
     */
    public Optional<RPGEntity> getEntity(UUID entityId) {
        return Optional.ofNullable(entities.get(entityId));
    }

    /**
     * Get all alive entities.
     */
    public List<RPGEntity> getAliveEntities() {
        List<RPGEntity> alive = new ArrayList<>();
        for (Map.Entry<UUID, RPGEntity> entry : entities.entrySet()) {
            if (!deadEntities.contains(entry.getKey())) {
                alive.add(entry.getValue());
            }
        }
        return alive;
    }

    /**
     * Get all dead entities.
     */
    public List<RPGEntity> getDeadEntities() {
        List<RPGEntity> dead = new ArrayList<>();
        for (UUID id : deadEntities) {
            RPGEntity entity = entities.get(id);
            if (entity != null) {
                dead.add(entity);
            }
        }
        return dead;
    }

    /**
     * Check if an entity is alive.
     */
    public boolean isAlive(UUID entityId) {
        return entities.containsKey(entityId) && !deadEntities.contains(entityId);
    }

    /**
     * Check if an entity is dead.
     */
    public boolean isDead(UUID entityId) {
        return deadEntities.contains(entityId);
    }

    /**
     * Clear all entities.
     */
    public void clear() {
        entities.clear();
        deadEntities.clear();
    }

    public void tick(long now) {
        entities.entrySet().forEach(entry -> entry.getValue().tick(now));
    }
}
