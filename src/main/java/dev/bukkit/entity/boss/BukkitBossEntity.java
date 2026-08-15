package dev.bukkit.entity.boss;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.stat.BukkitBossStatManager;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.boss.BossRuntime;
import dev.core.entity.boss.BossStage;
import dev.core.entity.boss.RPGBossEntity;
import dev.core.event.EventBusInterface;
import dev.core.game.TaskScheduler;

/**
 * Bukkit wrapper around a boss entity. Shares the bukkit entity's uuid so that
 * {@link EntityManager} damage routing and this wrapper can communicate.
 */
public class BukkitBossEntity extends RPGBossEntity {

    private final BossRuntime bossRuntime;
    private final BukkitBossStatManager bukkitStatManager;
    private final BossBarController bossBar;

    public BukkitBossEntity(UUID uuid, String name, EventBusInterface eventBus, TaskScheduler scheduler) {
        super(uuid, name, BukkitEffectManager.getInstance(), eventBus, scheduler);
        EntityManager.getInstance().registerEntity(this);
        Entity entity = Bukkit.getEntity(uuid);
        if (entity == null) {
            throw new IllegalStateException("No bukkit entity exists for boss uuid " + uuid);
        }
        this.bukkitStatManager = new BukkitBossStatManager(entity, getStatManager());
        this.bossBar = new BossBarController(name);
        this.bossRuntime = new BossRuntime(eventBus) {
            @Override
            protected void registerBossTriggers() {
                registerTrigger(BukkitBossEntity.this::handleDamage, EntityDamageByEntityEvent.class);
            }
        };
        bossRuntime.register();
    }

    public void onSpawn(Location location) {
        bossBar.setVisibleToPlayers(Bukkit.getOnlinePlayers());
        BossBarController.hideVanillaBossBar(getLivingEntity().orElse(null));
    }

    @Override
    public void tick(long now) {
        super.tick(now);
        if (isAlive() && !isDefeatSequenceActive()) {
            bukkitStatManager.tick(now, this::onDeath, getHealth(), getMaxHealth());
            updateBossBar();
            updateName();
        }
    }

    /** Show the boss's RPG health in its custom name as {@code [❤] current/max}. */
    private void updateName() {
        getLivingEntity().ifPresent(living -> {
            String base = ChatColor.translateAlternateColorCodes('&', getName());
            living.setCustomName(base + " [❤] " + Math.round(getHealth()) + "/" + Math.round(getMaxHealth()));
            living.setCustomNameVisible(true);
        });
    }

    @Override
    protected void onStageTransition(BossStage nextStage) {
        if (getDefeatStageId() != null && getDefeatStageId().equals(nextStage.getId())) {
            bossBar.remove();
            getLivingEntity().ifPresent(entity -> entity.setInvulnerable(true));
        }
    }

    @Override
    protected void onDefeatFinished() {
        getBukkitEntity().ifPresent(Entity::remove);
    }

    public Optional<LivingEntity> getLivingEntity() {
        Entity entity = Bukkit.getEntity(getUuid());
        return entity instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
    }

    public Optional<Entity> getBukkitEntity() {
        return Optional.ofNullable(Bukkit.getEntity(getUuid()));
    }

    @Override
    protected void playHitReaction(RPGEntity attacker) {
        getLivingEntity().ifPresent(living -> living.damage(0.001, BukkitPlayerEntity.bukkitSourceOf(attacker)));
    }

    private void handleDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity().getUniqueId().equals(getUuid())) {
            updateBossBar();
        }
    }

    private void updateBossBar() {
        double health = getHealth();
        double maxHealth = getMaxHealth();
        float pct = maxHealth <= 0 ? 0f : (float) Math.max(0.0, Math.min(1.0, health / maxHealth));
        bossBar.updateProgress(pct);
    }

    public BossBarController getBossBar() {
        return bossBar;
    }

    public void shutdown() {
        bossRuntime.unregister();
        bossBar.remove();
    }
}
