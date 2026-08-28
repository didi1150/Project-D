package dev.bukkit.entity.boss;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;

import dev.bukkit.DMain;
import dev.core.entity.EntityManager;
import dev.core.entity.boss.BossDefinition;
import dev.core.entity.boss.FloorData;
import dev.core.entity.boss.FloorDataRegistry;
import dev.core.event.EventBusInterface;
import dev.core.game.TaskScheduler;
import dev.core.utils.ColorCodes;

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

    public BukkitBossEntity spawn(BossDefinition definition, World world, Location location) {
        FloorData fd = FloorDataRegistry.getInstance().getOrEmpty(definition.getFloor());
        return spawn(definition, world, location, fd);
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

        BukkitBossEntity boss = new BukkitBossEntity(living.getUniqueId(), definition.getDisplayName(), eventBus,
                scheduler);
        boss.configure(definition,
                new BukkitBossEntityContext(() -> boss.getBukkitEntity().map(Entity::getLocation).orElse(null)),
                floorData);
        boss.onSpawn(location);
        return boss;
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
        living.setCustomName(ColorCodes.translate(definition.getDisplayName()));
        living.setCustomNameVisible(true);
        living.setRemoveWhenFarAway(false);
        living.setPersistent(true);
        living.setAI(false);
        living.setCollidable(false);
        living.setMetadata("BOSS", new FixedMetadataValue(DMain.getInstance(), true));
        living.setMetadata("DUNGEON", new FixedMetadataValue(DMain.getInstance(), true));
    }

    private void applyTypeSpecificSetup(LivingEntity living, EntityType type) {
        switch (type) {
        case ENDER_DRAGON:
            living.setGravity(false);
            ((org.bukkit.entity.EnderDragon) living).setPhase(org.bukkit.entity.EnderDragon.Phase.HOVER);
            break;
        default:
            break;
        }
    }
}
