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
import dev.core.entity.RPGEntity;
import dev.core.entity.boss.BossStage;
import dev.core.entity.boss.RPGBossEntity;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.TaskScheduler;
import dev.core.utils.AdventureText;
import dev.core.utils.ColorCodes;

/**
 * Bukkit wrapper around a boss entity. Shares the bukkit entity's uuid so that
 * {@link dev.core.entity.EntityManager} damage routing and this wrapper can
 * communicate.
 */
public class BukkitBossEntity extends RPGBossEntity {

    private final EventBusInterface eventBus;
    private final EventAction<EntityDamageByEntityEvent> damageAction;
    private final BukkitBossStatManager bukkitStatManager;
    private final BossBarController bossBar;
    private final LivingEntity bukkitEntity;
    private Location spawnLocation;
    private Float displayProgress;
    private String displayLabel;

    public BukkitBossEntity(LivingEntity entity, String name, EventBusInterface eventBus, TaskScheduler scheduler) {
        super(entity.getUniqueId(), name, BukkitEffectManager.getInstance(), eventBus, scheduler,
                BukkitStatusEffectManager.getInstance());
        this.bukkitEntity = entity;
        setTargetingStrategy(BukkitBossStrategyRegistry.firstAlivePlayerTarget());
        this.bukkitStatManager = new BukkitBossStatManager(entity, getStatManager());
        this.bossBar = new BossBarController(name);
        this.eventBus = eventBus;
        this.damageAction = new EventAction<>(this::handleDamage, EntityDamageByEntityEvent.class);
        this.eventBus.subscribe(damageAction);
    }

    public BukkitBossEntity(UUID uuid, String name, EventBusInterface eventBus, TaskScheduler scheduler) {
        this(requireLivingEntity(uuid), name, eventBus, scheduler);
    }

    private static LivingEntity requireLivingEntity(UUID uuid) {
        Entity entity = Bukkit.getEntity(uuid);
        if (!(entity instanceof LivingEntity living)) {
            throw new IllegalStateException("No living bukkit entity exists for boss uuid " + uuid);
        }
        return living;
    }

    public void onSpawn(Location location) {
        this.spawnLocation = location.clone();
        bossBar.setVisibleToPlayers(Bukkit.getOnlinePlayers());
        BossBarController.hideVanillaBossBar(getLivingEntity().orElse(null));
    }

    public Location getSpawnLocation() {
        return spawnLocation == null ? null : spawnLocation.clone();
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
            if (displayLabel != null) {
                living.customName(AdventureText.legacy(displayLabel));
                living.setCustomNameVisible(true);
                return;
            }
            String base = ColorCodes.translate(getName());
            double current = getHealth();
            double max = getMaxHealth();
            double absorption = getAbsorptionAmount();
            String color = absorption > 0 ? "§e" : "";
            living.customName(AdventureText
                    .legacy(color + base + " [❤] " + Math.round(current + absorption) + "/" + Math.round(max)));
            living.setCustomNameVisible(true);
        });
    }

    public void setDisplayOverride(float progress, String label) {
        this.displayProgress = Math.max(0.0f, Math.min(1.0f, progress));
        this.displayLabel = label;
    }

    public void clearDisplayOverride() {
        this.displayProgress = null;
        this.displayLabel = null;
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
        // Do not gate on isValid(): Paper marks entities in non-entity-ticking
        // chunks as invalid and masks them from Bukkit.getEntity(UUID), even
        // though the entity exists and can be controlled. A boss spawned in a
        // freshly loaded arena is often outside the players' ticking radius on
        // its first tick; treating it as missing made the initial stage fail
        // (e.g. Grow never applied its damage immunity).
        if (!bukkitEntity.isDead()) {
            return Optional.of(bukkitEntity);
        }
        Entity entity = Bukkit.getEntity(getUuid());
        return entity instanceof LivingEntity living && !living.isDead() ? Optional.of(living) : Optional.empty();
    }

    public Optional<Entity> getBukkitEntity() {
        if (!bukkitEntity.isDead()) {
            return Optional.of(bukkitEntity);
        }
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
        if (displayProgress != null) {
            bossBar.update(displayLabel != null ? displayLabel : ColorCodes.translate(getName()), displayProgress);
            return;
        }
        double health = getHealth();
        double maxHealth = getMaxHealth();
        float pct = maxHealth <= 0 ? 0f : (float) Math.max(0.0, Math.min(1.0, health / maxHealth));
        bossBar.updateProgress(pct);
    }

    public void shutdown() {
        try {
            stopStages();
        } finally {
            try {
                eventBus.unsubscribe(damageAction);
            } finally {
                bossBar.remove();
            }
        }
    }
}
