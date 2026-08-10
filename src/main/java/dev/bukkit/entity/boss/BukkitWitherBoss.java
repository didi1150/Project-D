package dev.bukkit.entity.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import dev.bukkit.ability.BukkitEffectManager;
import dev.core.entity.EntityManager;
import dev.core.entity.boss.BossRuntime;
import dev.core.entity.boss.BossStage;
import dev.core.entity.boss.BossStageContext;
import dev.core.entity.boss.RPGBossEntity;
import dev.core.event.EventBusInterface;
import dev.core.game.ScheduledTask;

public class BukkitWitherBoss extends RPGBossEntity {

    private final EventBusInterface eventBus;
    private final BossRuntime bossRuntime;
    private final BossBarController bossBar;
    private final List<UUID> trackedPlayers = new ArrayList<>();
    private long lastShotAt;
    private boolean transformationTriggered;

    public BukkitWitherBoss(UUID uuid, String name, EventBusInterface eventBus) {
        super(uuid, name, BukkitEffectManager.getInstance(), eventBus);
        EntityManager.getInstance().registerEntity(this);
        this.eventBus = eventBus;
        this.bossRuntime = new BossRuntime(eventBus) {
            @Override
            protected void registerBossTriggers() {
                registerTrigger(BukkitWitherBoss.this::handleTransformationChat, AsyncPlayerChatEvent.class);
                registerTrigger(BukkitWitherBoss.this::handleDamage, EntityDamageByEntityEvent.class);
            }
        };
        this.bossBar = new BossBarController("Wither Boss");

        configureStages();
        bossRuntime.register();
    }

    public BossRuntime getBossRuntime() {
        return bossRuntime;
    }

    private void configureStages() {
        getStageManager().addStage(new BossStage() {
            private ScheduledTask timer;

            @Override
            public String getId() {
                return "stage1";
            }

            @Override
            public void onEnter(BossStageContext context) {
                timer = getStageManager().getScheduler().runTaskTimer(() -> {
                    Bukkit.broadcastMessage("[Debug]: The boss is currently in Stage 1");
                }, 20, 20);
            }

            @Override
            public void onExit(BossStageContext context) {
                timer.cancel();
                Bukkit.broadcastMessage("[DEBUG]: Stage 1 exit");
            }

            @Override
            public boolean shouldTransition(BossStageContext context, long now) {
                return transformationTriggered;
            }

            @Override
            public String getNextStageId(BossStageContext context) {
                return "stage2";
            }
        });

        getStageManager().addStage(new BossStage() {
            @Override
            public String getId() {
                return "stage2";
            }

            @Override
            public boolean shouldTransition(BossStageContext context, long now) {
                return getHealth() <= 0;
            }

            @Override
            public void onEnter(BossStageContext context) {
                Bukkit.broadcastMessage("[DEBUG]: Stage 2 enter");
            }

            @Override
            public void onExit(BossStageContext context) {
                Bukkit.broadcastMessage("[DEBUG]: Stage 2 exit");
                bossBar.remove();
            }

            @Override
            public String getNextStageId(BossStageContext context) {
                return null;
            }
        });

        setInitialStage("stage1");
    }

    public void spawn(Location spawnLocation) {
//        org.bukkit.entity.Entity entity = spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.WITHER);
//        if (!(entity instanceof LivingEntity living)) {
//            throw new IllegalStateException("Failed to spawn wither boss entity");
//        }
//        
//        living.setCustomName("§c§lWither Boss");
//        living.setCustomNameVisible(true);
//        living.setRemoveWhenFarAway(false);
//        living.setAI(false);
        for (Player player : Bukkit.getOnlinePlayers()) {
            registerTrackedPlayer(player);
        }
        this.bossBar.setVisibleToPlayers(Bukkit.getOnlinePlayers());
    }

    public void tick(long now) {
        super.tick(now);

        if (getCurrentStage().map(stage -> stage.getId().equals("stage1")).orElse(false)) {
//            if (now - lastShotAt > 1200L) {
//                fireSkulls(1);
//                lastShotAt = now;
//            }
        } else if (getCurrentStage().map(stage -> stage.getId().equals("stage2")).orElse(false)) {
            if (now - lastShotAt > 900L) {
                fireSkulls(2);
                lastShotAt = now;
            }
        }

        updateBossBar();
    }

    private void fireSkulls(int count) {
        Player target = getNearestPlayer();
        if (target == null) {
            return;
        }

        Location origin = getBukkitEntityLocation();
        if (origin == null) {
            return;
        }

        for (int i = 0; i < count; i++) {
            Location projectileOrigin = origin.clone().add(0, 1.2, 0);
            Location aim = target.getLocation().clone().add(0, 1.0, 0);
            WitherSkull skull = origin.getWorld().spawn(projectileOrigin, WitherSkull.class);
            skull.setCharged(true);
            skull.setDirection(aim.toVector().subtract(projectileOrigin.toVector()).normalize());
        }
    }

    private Player getNearestPlayer() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() == null) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(getBukkitEntityLocation());
            if (dist < bestDistance) {
                bestDistance = dist;
                best = player;
            }
        }
        return best;
    }

    private Location getBukkitEntityLocation() {
        org.bukkit.entity.Entity entity = Bukkit.getEntity(getUuid());
        return entity == null ? null : entity.getLocation();
    }

    public void registerTrackedPlayer(Player player) {
        trackedPlayers.add(player.getUniqueId());
        bossBar.addViewer(player);
    }

    public void unregisterTrackedPlayer(Player player) {
        trackedPlayers.remove(player.getUniqueId());
        bossBar.removeViewer(player);
    }

    private void updateBossBar() {
        double health = getHealth();
        double maxHealth = getMaxHealth();
        float pct = maxHealth <= 0 ? 0f : (float) Math.max(0.0, Math.min(1.0, health / maxHealth));
        bossBar.updateProgress(pct);
    }

    public void handleTransformationChat(AsyncPlayerChatEvent event) {
        if ("Transformation".equalsIgnoreCase(event.getMessage().trim())) {
            transformationTriggered = true;
            event.setCancelled(true);
        }
    }

    public void handleDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity().getUniqueId().equals(getUuid())) {
            updateBossBar();
        }
    }

    public void shutdown() {
        bossRuntime.unregister();
    }
}
