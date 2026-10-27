package dev.bukkit.storage.progression;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.event.BukkitEventBus;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.event.impl.PlayerClassXpChangedEvent;
import dev.core.progression.PlayerClassProgression;
import dev.core.progression.PlayerProgression;
import dev.core.storage.database.ProgressionCacheStrategy;
import dev.core.storage.database.ProgressionDatabaseStrategy;

public class ClassProgressionService {

    private final ProgressionCacheStrategy cache;
    private final ProgressionDatabaseStrategy database;
    private final EventBusInterface eventBus;

    public ClassProgressionService(ProgressionCacheStrategy cache, ProgressionDatabaseStrategy database) {
        this(cache, database, BukkitEventBus.getInstance());
    }

    public ClassProgressionService(ProgressionCacheStrategy cache, ProgressionDatabaseStrategy database,
            EventBusInterface eventBus) {
        this.cache = cache;
        this.database = database;
        this.eventBus = eventBus;
    }

    public PlayerClassProgression getProgression(UUID playerId, RPGClassType type) {
        PlayerProgression live = liveProgression(playerId);
        if (live != null) {
            PlayerClassProgression progression = live.getProgression(type);
            if (progression != null) {
                return progression;
            }
        }
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
        int oldXp = prog.getXp();
        int oldLevel = prog.getLevel();
        prog.addXp(xp);
        cache.put(playerId, prog);
        database.save(playerId, prog);
        publishXpChange(playerId, type, prog, oldXp, oldLevel);
    }

    /**
     * Sets a class's XP through the service so the same change event fires for
     * commands and future XP sources.
     */
    public void setExperience(UUID playerId, RPGClassType type, int xp) {
        PlayerClassProgression prog = getProgression(playerId, type);
        int oldXp = prog.getXp();
        int oldLevel = prog.getLevel();
        prog.setXp(xp);
        cache.put(playerId, prog);
        database.save(playerId, prog);
        publishXpChange(playerId, type, prog, oldXp, oldLevel);
    }

    /**
     * Saves the live, local player progression (the source of truth while a
     * player entity is registered). Falls back to the cache only when the player
     * no longer has an in-memory entity.
     */
    public void saveAll(UUID playerId) {
        PlayerProgression live = liveProgression(playerId);
        if (live != null) {
            saveAll(live);
            return;
        }
        database.saveAll(playerId, cache.getAll(playerId));
        database.setActiveClass(playerId, cache.getActiveClass(playerId));
    }

    public void saveAll(PlayerProgression playerProgression) {
        if (playerProgression == null) {
            return;
        }
        UUID playerId = playerProgression.getPlayerId();
        Map<RPGClassType, PlayerClassProgression> progressions = playerProgression.getAllProgressions();
        for (PlayerClassProgression progression : progressions.values()) {
            cache.put(playerId, progression);
        }
        database.saveAll(playerId, progressions);
        RPGClassType activeClass = playerProgression.getActiveClass() == null ? RPGClassType.NONE
                : playerProgression.getActiveClass();
        cache.setActiveClass(playerId, activeClass);
        database.setActiveClass(playerId, activeClass);
        database.saveUnlockedItems(playerId, playerProgression.getUnlockedItems());
    }

    /**
     * Saves every registered player's live progression. Called at run boundaries
     * and on plugin shutdown so a server restart cannot lose earned XP.
     */
    public void saveAllRegisteredPlayers() {
        Set<UUID> saved = new HashSet<>();
        List<RPGEntity> entities = EntityManager.getInstance().getAliveEntities();
        entities.addAll(EntityManager.getInstance().getDeadEntities());
        for (RPGEntity entity : entities) {
            if (entity instanceof BukkitPlayerEntity playerEntity && saved.add(playerEntity.getUuid())) {
                saveAll(playerEntity.getPlayerProgression());
            }
        }
    }

    /**
     * Overlays persisted class progressions and unlocks into a fresh local
     * progression object. Used when a player entity is first created.
     */
    public void hydrate(PlayerProgression local) {
        if (local == null) {
            return;
        }
        UUID playerId = local.getPlayerId();
        Map<RPGClassType, PlayerClassProgression> stored = database.loadAll(playerId);
        for (RPGClassType type : RPGClassType.values()) {
            PlayerClassProgression localProgression = local.getProgression(type);
            if (localProgression == null) {
                local.unlockClass(type);
                localProgression = local.getProgression(type);
            }
            PlayerClassProgression persisted = stored.get(type);
            if (persisted != null) {
                localProgression.setLevel(persisted.getLevel());
                if (persisted.getXp() > 0 || persisted.getLevel() > 0) {
                    localProgression.setXp(persisted.getXp());
                }
                localProgression.setUsableItems(persisted.getUsableItems());
            }
            cache.put(playerId, localProgression);
        }
        local.replaceUnlockedItems(database.loadUnlockedItems(playerId));
        cache.setActiveClass(playerId, database.getActiveClass(playerId));
    }

    /**
     * Reloads a local progression from the persisted store. Used on rejoin when
     * the previous local state may belong to a discarded entity.
     */
    public void refreshFromStore(PlayerProgression local) {
        hydrate(local);
    }

    public void setActiveClass(PlayerProgression playerProgression) {
        cache.setActiveClass(playerProgression.getPlayerId(), playerProgression.getActiveClass());
        database.setActiveClass(playerProgression.getPlayerId(), playerProgression.getActiveClass());
    }

    /**
     * Reconciles the persisted active class of every registered player with the
     * actual active class held in their local {@link PlayerProgression} — the
     * state the running game actually assigned. Keeps the cache's activeClasses
     * map and the database in sync with the local source of truth.
     */
    public void syncActiveClasses() {
        List<RPGEntity> entities = EntityManager.getInstance().getAliveEntities();
        entities.addAll(EntityManager.getInstance().getDeadEntities());
        for (RPGEntity entity : entities) {
            if (!(entity instanceof BukkitPlayerEntity playerEntity)) {
                continue;
            }
            PlayerProgression progression = playerEntity.getPlayerProgression();
            RPGClassType activeClass = progression.getActiveClass() != null
                    ? progression.getActiveClass()
                    : RPGClassType.NONE;
            cache.setActiveClass(progression.getPlayerId(), activeClass);
            database.setActiveClass(progression.getPlayerId(), activeClass);
        }
    }

    public RPGClassType getActiveClass(UUID playerId) {
        PlayerProgression live = liveProgression(playerId);
        if (live != null && live.getActiveClass() != null) {
            return live.getActiveClass();
        }
        RPGClassType cachedActiveClassType = cache.getActiveClass(playerId);
        if (cachedActiveClassType == RPGClassType.NONE) {
            cache.setActiveClass(playerId, database.getActiveClass(playerId));
        }
        return cache.getActiveClass(playerId);
    }

    public Set<String> getUnlockedItems(UUID playerId) {
        PlayerProgression live = liveProgression(playerId);
        if (live != null) {
            return live.getUnlockedItems();
        }
        return database.loadUnlockedItems(playerId);
    }

    public boolean isItemUnlocked(UUID playerId, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        return getUnlockedItems(playerId).contains(itemId);
    }

    /**
     * Permanently unlocks a reward-only item and persists the change immediately.
     *
     * @return true when the unlock was newly added
     */
    public boolean unlockItem(UUID playerId, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        PlayerProgression live = liveProgression(playerId);
        if (live != null) {
            boolean added = live.unlockItem(itemId);
            if (added) {
                database.saveUnlockedItems(playerId, live.getUnlockedItems());
            }
            return added;
        }
        Set<String> unlocked = new HashSet<>(database.loadUnlockedItems(playerId));
        boolean added = unlocked.add(itemId);
        if (added) {
            database.saveUnlockedItems(playerId, unlocked);
        }
        return added;
    }

    private void publishXpChange(UUID playerId, RPGClassType type, PlayerClassProgression progression, int oldXp,
            int oldLevel) {
        if (eventBus == null || progression == null) {
            return;
        }
        if (progression.getXp() == oldXp && progression.getLevel() == oldLevel) {
            return;
        }
        eventBus.sendEvent(new PlayerClassXpChangedEvent(playerId, type, oldXp, progression.getXp(), oldLevel,
                progression.getLevel()));
    }

    private PlayerProgression liveProgression(UUID playerId) {
        Optional<RPGEntity> entity = EntityManager.getInstance().getEntity(playerId);
        if (entity.isPresent() && entity.get() instanceof BukkitPlayerEntity playerEntity) {
            return playerEntity.getPlayerProgression();
        }
        return null;
    }
}
