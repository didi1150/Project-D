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

import dev.bukkit.ability.BukkitSpiritSceptreBatEffect;
import dev.bukkit.entity.BukkitEntityFactory;
import dev.bukkit.entity.VanillaEntityMeta;
import dev.bukkit.utils.DamageUtils;
import dev.core.entity.EntityManager;
import dev.core.event.EventAction;
import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;

@EventSubscriber
public class CancelSubscriber {

    private final Plugin plugin;

    public CancelSubscriber(Plugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntityType() == EntityType.PLAYER) {
            event.setCancelled(true);
        }

        if (event.getEntity() instanceof LivingEntity living) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> DamageUtils.updateName(living), 1L);
        }
    }

    @Subscribe
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @Subscribe
    public void onTargetOtherMob(EntityTargetLivingEntityEvent event) {
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
    }

    @Subscribe
    public void onDeathDrops(EntityDeathEvent event) {
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @Subscribe
    public void onEntityExplodeDamage(EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @Subscribe
    public void onBlockExplodeDamage(BlockExplodeEvent event) {
        event.blockList().clear();
    }

    @Subscribe
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != SpawnReason.CUSTOM && event.getSpawnReason() != SpawnReason.COMMAND)
            event.setCancelled(true);
        else {
            LivingEntity entity = event.getEntity();
            if (entity.isInvisible() || entity.hasMetadata(BukkitSpiritSceptreBatEffect.METADATA)) {
                return; // summoned projectiles (e.g. the Spirit Sceptre bat) get no level/health
                        // nametag
            }
            VanillaEntityMeta meta = new VanillaEntityMeta(1, BukkitEntityFactory.getRelation(entity));
            // Store metadata
            entity.setMetadata("VANILLA_META", new FixedMetadataValue(plugin, meta));
            entity.setNoDamageTicks(0);
            // Set initial custom name
            DamageUtils.updateName(entity);
        }
    }

    @Subscribe
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() == CreateReason.NETHER_PAIR) {
            event.setCancelled(true);
        }

        if (event.getReason() == CreateReason.FIRE) {
            if (event.getWorld().getName().equalsIgnoreCase("world")
                    || event.getWorld().getName().equalsIgnoreCase("lobby")) {
                event.setCancelled(true);
            }
        }
    }

    @Subscribe
    public void onEnterPortalEvent(EntityPortalEvent event) {
        event.setCancelled(true);
    }

    @Subscribe
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() == TeleportCause.NETHER_PORTAL) {
            event.setCanCreatePortal(false);
        }
        event.setCancelled(true);
    }

    @Subscribe(priority = EventAction.LOW_PRIORITY)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        Entity damager = event.getDamager();

        if (entity.hasMetadata("DUNGEON") && damager.hasMetadata("DUNGEON")) {
            event.setCancelled(true);
        }
    }

}
