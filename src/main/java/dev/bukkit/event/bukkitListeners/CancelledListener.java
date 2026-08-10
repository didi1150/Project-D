package dev.bukkit.event.bukkitListeners;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class CancelledListener implements Listener {

    private Plugin plugin;

    public CancelledListener(Plugin plugin) {
        this.plugin = plugin;

//        protocolManager.addPacketListener(
//                new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.ARM_ANIMATION) {
//                    @Override
//                    public void onPacketReceiving(PacketEvent event) {
//                        Player player = event.getPlayer();
//                        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
//                        if (optional.isEmpty()) {
//                            return;
//                        } else {
//                            optional.get().recordSwing();
//                            if (!optional.get().canAttack()) {
//                                event.setCancelled(true); // cancel arm swing packet
//                            }
//                        }
//                    }
//                });
//
//        protocolManager.addPacketListener(
//                new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.ADVANCEMENTS) {
//                    @Override
//                    public void onPacketSending(PacketEvent event) {
//                        event.setCancelled(true);
//                    }
//                });
    }

//    @EventHandler
//    public void onRegen(EntityRegainHealthEvent event) {
//        if (event.getEntityType() == EntityType.PLAYER) {
//            event.setCancelled(true);
//        }
//
//        if (event.getEntity() instanceof LivingEntity living) {
//            Bukkit.getScheduler().runTaskLater(plugin, () -> DamageUtils.updateName(living), 1L);
//        }
//    }
//
//    @EventHandler
//    public void onChangeFood(FoodLevelChangeEvent event) {
//        event.setCancelled(true);
//    }

//    @EventHandler(priority = EventPriority.MONITOR)
//    public void onDamage(EntityDamageEvent event) {
//        if (event.isCancelled()) {
//            return;
//        }
//        if (event.getEntity() instanceof LivingEntity le) {
//            Bukkit.getScheduler().runTaskLater(plugin, () -> {
//                DamageUtils.updateName(le);
//            }, 1L);
//        }
//    }

//    @EventHandler(priority = EventPriority.MONITOR)
//    public void onMobDamagedByOtherMob(EntityDamageByEntityEvent event) {
//        Entity entity = event.getEntity();
//        Entity damager = event.getDamager();
//
//        if (entity.hasMetadata("DUNGEON") && damager.hasMetadata("DUNGEON")) {
//            event.setCancelled(true);
//        }
//    }

//    @EventHandler
//    public void onTarget(EntityTargetLivingEntityEvent event) {
//        if (!(event.getEntity() instanceof Mob mob)) {
//            return;
//        }
//
//        LivingEntity target = event.getTarget();
//        if (target == null) {
//            return;
//        }
//
//        // Prevent mobs from targeting other mobs
//        if (target instanceof Player player) {
//            if (EntityManager.getInstance().isDead(player.getUniqueId())) {
//                event.setCancelled(true);
//                mob.setTarget(null);
//            }
//
//        } else {
//            if (event.getEntity().hasMetadata("DUNGEON") && target.hasMetadata("DUNGEON")) {
//                event.setCancelled(true);
//            }
//        }
//    }
//
//    @EventHandler
//    public void onDeathDrop(EntityDeathEvent event) {
//        event.getDrops().clear();
//        event.setDroppedExp(0);
//    }
//
//    @EventHandler
//    public void onExplode(EntityExplodeEvent event) {
//        event.blockList().clear();
//    }
//
//    @EventHandler
//    public void onExplode(BlockExplodeEvent event) {
//        event.blockList().clear();
//    }
//
//    @EventHandler
//    public void onLeavesDecay(LeavesDecayEvent event) {
//        event.setCancelled(true);
//    }
    
//    @EventHandler
//    public void onEntitySpawn(CreatureSpawnEvent event) {
//        if (event.getSpawnReason() == SpawnReason.CUSTOM) {
//            return;
//        }
//        event.setCancelled(true);
//        LivingEntity entity = event.getEntity();
//        if (entity.isInvisible()) {
//            return;
//        }
//
//        VanillaEntityMeta meta = new VanillaEntityMeta(1, BukkitEntityFactory.getRelation(entity));
//
//        // Store metadata
//        entity.setMetadata("VANILLA_META", new FixedMetadataValue(plugin, meta));
//
//        // Set initial custom name
//        DamageUtils.updateName(entity);
//    }

//    @EventHandler
//    public void onPortalCreate(PortalCreateEvent event) {
//        if (event.getReason() == CreateReason.NETHER_PAIR) {
//            event.setCancelled(true);
//        }
//        
//        if (event.getReason() == CreateReason.FIRE) {
//            if (event.getWorld().getName().equalsIgnoreCase("world") || event.getWorld().getName().equalsIgnoreCase("lobby")) {
//                event.setCancelled(true);
//            }
//        }
//    }
//
//    @EventHandler
//    public void onEnterPortal(EntityPortalEvent event) {
//        event.setCancelled(true);
//    }
//
//    @EventHandler
//    public void onEnterPortalPlayer(PlayerPortalEvent event) {
//        event.setCancelled(true);
//    }
}
