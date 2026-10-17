package dev.bukkit.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import dev.bukkit.DMain;
import dev.bukkit.entity.boss.BukkitBossEntity;
import dev.bukkit.entity.boss.BukkitBossFactory;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.game.scheduler.BukkitTaskScheduler;
import dev.bukkit.game.states.BossState;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.boss.BossDefinition;
import dev.core.entity.boss.FloorDataRegistry;
import dev.core.entity.mob.MobDefinition;
import dev.core.game.GameState;
import dev.core.game.GameStateController;

public class DebugRpgEntityService {

    private static DebugRpgEntityService instance;

    private final Set<UUID> debugUuids = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitBossEntity> debugBosses = new ConcurrentHashMap<>();

    public static DebugRpgEntityService getInstance() {
        if (instance == null) {
            instance = new DebugRpgEntityService();
        }
        return instance;
    }

    public LivingEntity spawnMob(MobDefinition definition, Location location, int level) {
        LivingEntity spawned = BukkitEntityFactory.spawnDebugMob(definition, location, level);
        if (spawned != null) {
            debugUuids.add(spawned.getUniqueId());
        }
        return spawned;
    }

    public BukkitBossEntity spawnBoss(BossDefinition definition, Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalStateException("Location has no world");
        }
        Location spawnLoc = location.clone();
        spawnLoc.setX(spawnLoc.getBlockX() + 0.5);
        spawnLoc.setZ(spawnLoc.getBlockZ() + 0.5);

        BukkitBossFactory factory = new BukkitBossFactory(BukkitEventBus.getInstance(),
                new BukkitTaskScheduler(DMain.getInstance()));
        BukkitBossEntity boss = factory.spawn(definition, world, spawnLoc,
                FloorDataRegistry.getInstance().getOrEmpty(definition.getFloor()));
        boss.getBukkitEntity().ifPresent(entity -> BukkitEntityMetadata.set(entity, "DEBUG_SPAWN", true));
        debugUuids.add(boss.getUuid());
        debugBosses.put(boss.getUuid(), boss);
        injectIntoBossStateIfActive(boss);
        return boss;
    }

    private void injectIntoBossStateIfActive(BukkitBossEntity boss) {
        try {
            GameStateController controller = DMain.getInstance().getGameStateController();
            if (controller == null) {
                return;
            }
            GameState current = controller.getCurrentState();
            if (current instanceof BossState bossState) {
                if (!bossState.hasActiveBoss()) {
                    bossState.injectDebugBoss(boss);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public boolean isDebugEntity(UUID uuid) {
        return debugUuids.contains(uuid);
    }

    public int count() {
        return debugUuids.size();
    }

    public List<UUID> getTrackedUuids() {
        return List.copyOf(debugUuids);
    }

    public boolean despawn(UUID uuid) {
        if (!debugUuids.contains(uuid)) {
            // Not tracked — still allow deregister if it is a debug mob/boss via metadata check
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && BukkitEntityMetadata.has(entity, "DEBUG_SPAWN")) {
                // treat as debug even if not tracked (e.g. after reload)
                debugUuids.add(uuid);
            } else {
                return false;
            }
        }
        BukkitBossEntity debugBoss = debugBosses.remove(uuid);
        if (debugBoss != null) {
            BukkitBossFactory factory = new BukkitBossFactory(BukkitEventBus.getInstance(),
                    new BukkitTaskScheduler(DMain.getInstance()));
            factory.despawn(debugBoss);
            debugUuids.remove(uuid);
            clearBossStateIfMatches(uuid);
            return true;
        }
        // Check EntityManager for mob or boss
        RPGEntity rpg = EntityManager.getInstance().getEntity(uuid).orElse(null);
        if (rpg instanceof BukkitBossEntity bossEntity) {
            BukkitBossFactory factory = new BukkitBossFactory(BukkitEventBus.getInstance(),
                    new BukkitTaskScheduler(DMain.getInstance()));
            factory.despawn(bossEntity);
            debugUuids.remove(uuid);
            debugBosses.remove(uuid);
            clearBossStateIfMatches(uuid);
            return true;
        }
        Entity bukkitEntity = Bukkit.getEntity(uuid);
        if (bukkitEntity != null) {
            bukkitEntity.remove();
        }
        EntityManager.getInstance().removeEntity(uuid);
        debugUuids.remove(uuid);
        clearBossStateIfMatches(uuid);
        return true;
    }

    private void clearBossStateIfMatches(UUID uuid) {
        try {
            GameStateController controller = DMain.getInstance().getGameStateController();
            if (controller == null) {
                return;
            }
            GameState current = controller.getCurrentState();
            if (current instanceof BossState bossState) {
                if (uuid.equals(bossState.getBossUuid())) {
                    bossState.clearInjectedBoss(uuid);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public int despawnNearest(Player player, double radius) {
        if (player == null) {
            return 0;
        }
        double bestSq = radius * radius;
        UUID best = null;
        Location origin = player.getLocation();
        for (UUID uuid : new ArrayList<>(debugUuids)) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null || !entity.isValid()) {
                continue;
            }
            double distSq = entity.getLocation().distanceSquared(origin);
            if (distSq <= bestSq) {
                if (best == null || distSq < bestSq) {
                    bestSq = distSq;
                    best = uuid;
                } else if (distSq == bestSq) {
                    // keep first
                }
            }
        }
        if (best == null) {
            // fallback: find nearest even if outside radius but still closest tracked
            // to give feedback; prefer no despawn if none in radius
            return 0;
        }
        boolean ok = despawn(best);
        return ok ? 1 : 0;
    }

    public int despawnAll() {
        List<UUID> copy = new ArrayList<>(debugUuids);
        int removed = 0;
        for (UUID uuid : copy) {
            if (despawn(uuid)) {
                removed++;
            }
        }
        // Safety: clear any remaining (e.g. dead entities)
        debugUuids.clear();
        debugBosses.clear();
        return removed;
    }

    public Collection<RPGEntity> getTrackedRpgEntities() {
        List<RPGEntity> result = new ArrayList<>();
        for (UUID uuid : debugUuids) {
            EntityManager.getInstance().getEntity(uuid).ifPresent(result::add);
        }
        return Collections.unmodifiableList(result);
    }

    public void shutdownAll() {
        despawnAll();
    }
}
