package dev.bukkit.entity.boss;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.entity.MobRPGEntity;
import dev.bukkit.stat.BukkitBossStatManager;
import dev.bukkit.status.BukkitStatusEffectManager;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.boss.BossRuntime;
import dev.core.entity.boss.BossStage;
import dev.core.entity.boss.RPGBossEntity;
import dev.core.event.EventBusInterface;
import dev.core.game.TaskScheduler;
import dev.core.utils.ColorCodes;

/**
 * Bukkit wrapper around a boss entity. Shares the bukkit entity's uuid so that
 * {@link EntityManager} damage routing and this wrapper can communicate.
 */
public class BukkitBossEntity extends RPGBossEntity {

    private final BossRuntime bossRuntime;
    private final BukkitBossStatManager bukkitStatManager;
    private final BossBarController bossBar;

    public BukkitBossEntity(UUID uuid, String name, EventBusInterface eventBus, TaskScheduler scheduler) {
        super(uuid, name, BukkitEffectManager.getInstance(), eventBus, scheduler, BukkitStatusEffectManager.getInstance());
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
            // Same vanilla damage-immunity window as dungeon mobs (see
            // MobRPGEntity.tick): clear it so rapid projectile hits always
            // land instead of bouncing off the boss. setNoDamageTicks maps to
            // Entity.invulnerableTime and setLastDamage to LivingEntity.lastHurt
            // on 1.21.8; the hurtServer denial is `invulnerableTime > 10 &&
            // amount <= lastHurt`, so both must be cleared.
            getLivingEntity().ifPresent(living -> {
                living.setNoDamageTicks(0);
                living.setLastDamage(0);
            });
            bukkitStatManager.tick(now, this::onDeath, getHealth(), getMaxHealth());
            updateBossBar();
            updateName();
        }
    }

    /** Show the boss's RPG health in its custom name as {@code [❤] current/max}. */
    private void updateName() {
        getLivingEntity().ifPresent(living -> {
            String base = ColorCodes.translate(getName());
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

    /**
     * Intentionally empty — see {@link MobRPGEntity#playHitReaction}: a
     * reentrant vanilla damage poke re-seeds the damage-immunity window
     * mid-event and makes the outer hit bounce (1.21.8
     * {@code invulnerableTime > 10 && amount <= lastHurt} denial). The outer
     * hit already plays the hurt flash, sound and knockback.
     */
    @Override
    protected void playHitReaction(RPGEntity attacker) {
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
