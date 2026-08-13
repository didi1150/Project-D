package dev.bukkit.event.subscribers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.PortalCreateEvent.CreateReason;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import dev.bukkit.entity.BukkitEntityFactory;
import dev.bukkit.entity.VanillaEntityMeta;
import dev.bukkit.utils.DamageUtils;
import dev.core.entity.EntityManager;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;

public class CancelSubscriber {

    private Plugin plugin;
    private EventBusInterface eventBus;

    public CancelSubscriber(EventBusInterface eventBus, Plugin plugin) {
        this.eventBus = eventBus;
        this.plugin = plugin;

        cancelRegainHealth();
        cancelFoodLevelChange();
        cancelTargetOtherMob();
        cancelDeathDrops();
        cancelBlockExplodeDamage();
        cancelCreatureSpawn();
        cancelDeathDrops();
        cancelEnterPortalEvent();
        cancelEntityExplodeDamage();
        cancelPlayerPortal();
        cancelPortalCreate();
        cancelEntityDamageByEntity();
    }

    private void cancelRegainHealth() {
        eventBus.subscribe(new EventAction<>(event -> {
            if (event.getEntityType() == EntityType.PLAYER) {
                event.setCancelled(true);
            }

            if (event.getEntity() instanceof LivingEntity living) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> DamageUtils.updateName(living), 1L);
            }
        }, EntityRegainHealthEvent.class, EventAction.NORMAL_PRIORITY));
    }

    private void cancelFoodLevelChange() {
        eventBus.subscribe(new EventAction<>(event -> {
            event.setCancelled(true);
        }, FoodLevelChangeEvent.class, EventAction.NORMAL_PRIORITY));
    }

    private void cancelTargetOtherMob() {
        eventBus.subscribe(new EventAction<>(event -> {
            if (!(event.getEntity() instanceof Mob mob)) {
                return;
            }

            LivingEntity target = event.getTarget();
            if (target == null) {
                return;
            }

            // Prevent mobs from targeting other mobs
            if (target instanceof Player player) {
                if (EntityManager.getInstance().isDead(player.getUniqueId())) {
                    event.setCancelled(true);
                    mob.setTarget(null);
                }

            } else {
                if (event.getEntity().hasMetadata("DUNGEON") && target.hasMetadata("DUNGEON")) {
                    event.setCancelled(true);
                }
            }
        }, EntityTargetLivingEntityEvent.class, EventAction.NORMAL_PRIORITY));
    }

    private void cancelDeathDrops() {
        eventBus.subscribe(new EventAction<>(event -> {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }, EntityDeathEvent.class, EventAction.NORMAL_PRIORITY));
    }

    private void cancelEntityExplodeDamage() {
        eventBus.subscribe(new EventAction<>(event -> {
            event.blockList().clear();
        }, EntityExplodeEvent.class, EventAction.NORMAL_PRIORITY));
    }

    private void cancelBlockExplodeDamage() {
        eventBus.subscribe(new EventAction<>(event -> {
            event.blockList().clear();
        }, BlockExplodeEvent.class, EventAction.NORMAL_PRIORITY));
    }

    private void cancelCreatureSpawn() {
        eventBus.subscribe(new EventAction<>(event -> {
            if (event.getSpawnReason() != SpawnReason.CUSTOM && event.getSpawnReason() != SpawnReason.COMMAND)
                event.setCancelled(true);
            else {
                LivingEntity entity = event.getEntity();
                if (entity.isInvisible()) {
                    return;
                }
                VanillaEntityMeta meta = new VanillaEntityMeta(1, BukkitEntityFactory.getRelation(entity));
                // Store metadata
                entity.setMetadata("VANILLA_META", new FixedMetadataValue(plugin, meta));
                // Set initial custom name
                DamageUtils.updateName(entity);
            }
        }, CreatureSpawnEvent.class, EventAction.NORMAL_PRIORITY));
    }

    private void cancelPortalCreate() {
        eventBus.subscribe(new EventAction<>(event -> {
            if (event.getReason() == CreateReason.NETHER_PAIR) {
                event.setCancelled(true);
            }

            if (event.getReason() == CreateReason.FIRE) {
                if (event.getWorld().getName().equalsIgnoreCase("world")
                        || event.getWorld().getName().equalsIgnoreCase("lobby")) {
                    event.setCancelled(true);
                }
            }
        }, PortalCreateEvent.class, EventAction.NORMAL_PRIORITY));
    }

    private void cancelEnterPortalEvent() {
        eventBus.subscribe(new EventAction<>(event -> {
            event.setCancelled(true);
        }, EntityPortalEvent.class, EventAction.NORMAL_PRIORITY));
    }

    private void cancelPlayerPortal() {
        eventBus.subscribe(new EventAction<>(event -> {
            if (event.getCause() == TeleportCause.NETHER_PORTAL) {
                event.setCanCreatePortal(false);
            }
            event.setCancelled(true);
        }, PlayerPortalEvent.class, EventAction.NORMAL_PRIORITY));
    }

    private void cancelEntityDamageByEntity() {
        eventBus.subscribe(new EventAction<>(event -> {
            Entity entity = event.getEntity();
            Entity damager = event.getDamager();

            if (entity.hasMetadata("DUNGEON") && damager.hasMetadata("DUNGEON")) {
                event.setCancelled(true);
            }
        }, EntityDamageByEntityEvent.class, EventAction.LOW_PRIORITY));
    }

}
