package dev.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import dev.bukkit.utils.DamageUtils;
import dev.core.ability.AbilityAction;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityManager;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.mob.MobDefinition;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.stat.StatManager;

import java.util.Optional;

/**
 * The RPG facade of a dungeon mob. Registered in {@link EntityManager} so all
 * damage routes through {@link #dealRPGDamage} against its config-driven
 * {@link StatManager}; drives the health-sync onto the vanilla entity, refreshes
 * the RPG-health custom name, casts the equipped weapon's abilities at nearby
 * non-ghost players, and cleans itself up on death.
 *
 * <p>
 * Vanilla AI keeps moving the mob (this facade extends {@link RPGEntity} — not
 * {@code RPGMobEntity} — so it has no boss-style movement/attack patterns).
 */
public class MobRPGEntity extends RPGEntity {

    private final LivingEntity vanilla;
    private final MobDefinition definition;
    private final Optional<MobBehavior> behavior;
    private long nextCastAt = 0;

    public MobRPGEntity(LivingEntity vanilla, MobDefinition definition, StatManager baseStats,
            EffectManagerInterface effectManager, EventBusInterface eventBus) {
        super(baseStats, vanilla.getUniqueId(), vanilla.getName(), EntityType.MOB, effectManager, eventBus,
                RPGClassType.NONE);
        this.vanilla = vanilla;
        this.definition = definition;
        this.behavior = MobBehaviorRegistry.getInstance().get(definition.getBehaviorId());
        setAbilityDamageMultiplier(definition.getAbilityDamageMultiplier());
    }

    @Override
    public void tick(long now) {
        super.tick(now); // statManager + equipmentManager + checkAlive (may trigger onDeath)
        if (!isAlive()) {
            return;
        }
        syncVanillaHealth();
        if (definition.getDisplayName() != null) {
            DamageUtils.updateName(vanilla);
        }
        tryCastAbility(now);
        behavior.ifPresent(b -> b.onTick(this, vanilla, now));
    }

    @Override
    protected void playHitReaction(RPGEntity attacker) {
        if (vanilla.isValid()) {
            vanilla.damage(0.001, BukkitPlayerEntity.bukkitSourceOf(attacker));
        }
    }

    @Override
    public void onDeath() {
        behavior.ifPresent(b -> b.onDeath(this, vanilla));
        super.onDeath();
        // Natural death animation for the vanilla mob; remove the RPG facade.
        if (vanilla.isValid() && !vanilla.isDead()) {
            vanilla.setHealth(0);
        }
        EntityManager.getInstance().removeEntity(getUuid());
    }

    /** Invoked by the spawn factory once the mob is fully set up (stats, equipment, boss bar). */
    public void triggerSpawnBehavior(LivingEntity vanilla) {
        behavior.ifPresent(b -> b.onSpawn(this, vanilla));
    }

    /** Scale the vanilla entity's health to the RPG health ratio (mirrors BukkitBossStatManager). */
    private void syncVanillaHealth() {
        double current = getHealth();
        double max = getMaxHealth();
        if (max <= 0) {
            return;
        }
        double vanillaMax = vanilla.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (vanillaMax <= 0) {
            vanillaMax = 1;
        }
        double scaled = (current / max) * vanillaMax;
        if (vanilla.isValid()) {
            vanilla.setHealth(Math.max(0.5, Math.min(scaled, vanillaMax)));
        }
    }

    private void tryCastAbility(long now) {
        if (definition.getMainHandItemId() == null || now < nextCastAt) {
            return;
        }
        nextCastAt = now + definition.getAbilityCastInterval() * 50L;

        Player target = nearestNonGhostPlayer(14);
        if (target == null || target.isDead() || !target.isOnline()) {
            return;
        }
        // Face the target so the thrown projectile flies toward it.
        Location loc = vanilla.getLocation();
        loc.setDirection(target.getLocation().toVector().subtract(loc.toVector()));
        vanilla.setRotation(loc.getYaw(), loc.getPitch());
        triggerAbility(AbilityAction.RIGHT_CLICK);
    }

    private Player nearestNonGhostPlayer(double range) {
        Player nearest = null;
        double bestSq = range * range;
        for (Player player : vanilla.getWorld().getPlayers()) {
            if (player.isDead() || !player.isOnline()
                    || EntityManager.getInstance().isSpectator(player.getUniqueId())) {
                continue; // skip ghosts registered in the EntityManager
            }
            double distSq = player.getLocation().distanceSquared(vanilla.getLocation());
            if (distSq < bestSq) {
                bestSq = distSq;
                nearest = player;
            }
        }
        return nearest;
    }

    public MobDefinition getDefinition() {
        return definition;
    }
}