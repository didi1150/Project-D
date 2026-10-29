package dev.bukkit.entity.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import dev.bukkit.entity.BukkitEntityMetadata;
import dev.bukkit.utils.BukkitText;
import dev.core.entity.EntityManager;
import dev.core.entity.boss.BossDefinition;
import dev.core.entity.boss.BossEntityContext;
import dev.core.entity.boss.FloorData;
import dev.core.event.EventBusInterface;
import dev.core.game.TaskScheduler;
import dev.core.utils.AdventureText;

/**
 * Spawns the bukkit entity for a {@link BossDefinition} and wraps it in a
 * {@link BukkitBossEntity} that shares the entity's uuid.
 */
public class BukkitBossFactory {

    private final EventBusInterface eventBus;
    private final TaskScheduler scheduler;

    public BukkitBossFactory(EventBusInterface eventBus, TaskScheduler scheduler) {
        this.eventBus = eventBus;
        this.scheduler = scheduler;
    }

    public BukkitBossEntity spawn(BossDefinition definition, World world, Location location, FloorData floorData) {
        EntityType bukkitType;
        try {
            bukkitType = EntityType.valueOf(definition.getEntityType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unknown boss entity type: " + definition.getEntityType(), e);
        }
        if (!bukkitType.isAlive()) {
            throw new IllegalStateException("Boss entity type is not alive: " + bukkitType);
        }

        Entity spawned = world.spawnEntity(location, bukkitType);
        if (!(spawned instanceof LivingEntity living)) {
            throw new IllegalStateException("Failed to spawn boss entity of type: " + bukkitType);
        }

        applySetup(living, definition);
        applyTypeSpecificSetup(living, bukkitType);

        BukkitBossEntity boss;
        try {
            boss = createBoss(living, definition, location, floorData);
        } catch (RuntimeException e) {
            living.remove();
            throw e;
        }
        EntityManager.getInstance().registerEntity(boss);
        if (definition.getStages().isEmpty()) {
            Bukkit.getLogger().warning("[Boss] Definition '" + definition.getId()
                    + "' has no stages; the boss will be damageable.");
        }
        Bukkit.getLogger().info("[Boss] Spawned " + definition.getId() + " (floor " + definition.getFloor()
                + ") initialStage=" + boss.getCurrentStageId() + " damageImmune=" + boss.isDamageImmune()
                + " stages=" + definition.getStages().size());
        return boss;
    }

    private BukkitBossEntity createBoss(LivingEntity living, BossDefinition definition, Location location,
            FloorData floorData) {
        BukkitBossEntity boss = new BukkitBossEntity(living, definition.getDisplayName(), eventBus, scheduler);
        try {
            boss.configure(definition,
                    new BossEntityContextImpl(() -> boss.getBukkitEntity().map(Entity::getLocation).orElse(null)),
                    floorData);
            boss.onSpawn(location);
            return boss;
        } catch (RuntimeException e) {
            try {
                boss.shutdown();
            } catch (RuntimeException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    public void despawn(BukkitBossEntity boss) {
        if (boss == null) {
            return;
        }
        boss.getBukkitEntity().ifPresent(Entity::remove);
        boss.shutdown();
        EntityManager.getInstance().removeEntity(boss.getUuid());
    }

    private void applySetup(LivingEntity living, BossDefinition definition) {
        living.customName(AdventureText.legacy(definition.getDisplayName()));
        living.setCustomNameVisible(true);
        living.setRemoveWhenFarAway(false);
        living.setPersistent(true);
        living.setAI(false);
        living.setCollidable(false);
        BukkitEntityMetadata.set(living, "BOSS", true);
        BukkitEntityMetadata.set(living, "DUNGEON", true);
    }

    private void applyTypeSpecificSetup(LivingEntity living, EntityType type) {
        switch (type) {
        case ENDER_DRAGON:
            living.setGravity(false);
            ((EnderDragon) living).setPhase(EnderDragon.Phase.HOVER);
            break;
        default:
            break;
        }
    }

    private static final class BossEntityContextImpl implements BossEntityContext {

        private static final Random RANDOM = new Random();

        private final Supplier<Location> bossLocation;

        private BossEntityContextImpl(Supplier<Location> bossLocation) {
            this.bossLocation = bossLocation;
        }

        @Override
        public void broadcast(String message) {
            BukkitText.broadcast(message);
        }

        @Override
        public List<UUID> spawnAdd(String entityType, int count) {
            Location origin = bossLocation.get();
            if (origin == null || origin.getWorld() == null) {
                return List.of();
            }
            EntityType type;
            try {
                type = EntityType.valueOf(entityType.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return List.of();
            }
            List<UUID> spawned = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Location loc = origin.clone().add(RANDOM.nextInt(5) - 2, 0.5, RANDOM.nextInt(5) - 2);
                Entity entity = origin.getWorld().spawnEntity(loc, type);
                BukkitEntityMetadata.set(entity, "BOSS_MINION", true);
                spawned.add(entity.getUniqueId());
            }
            return spawned;
        }
    }
}
