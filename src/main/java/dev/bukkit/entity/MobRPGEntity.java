package dev.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import dev.bukkit.utils.DamageUtils;
import dev.bukkit.utils.StealthRegistry;
import dev.bukkit.status.BukkitStatusEffectManager;
import dev.core.ability.Ability;
import dev.core.ability.AbilityAction;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityManager;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.mob.MobDefinition;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
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

    private static final double ABILITY_CAST_RANGE = 14;

    public MobRPGEntity(LivingEntity vanilla, MobDefinition definition, StatManager baseStats,
            EffectManagerInterface effectManager, EventBusInterface eventBus) {
        super(baseStats, vanilla.getUniqueId(), vanilla.getName(), EntityType.MOB, effectManager, eventBus,
                RPGClassType.NONE, BukkitStatusEffectManager.getInstance());
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
        // Clear the vanilla damage-immunity window (20 ticks after each hit):
        // while it is active, hits are denied BEFORE any Bukkit damage event
        // fires, so rapid arrows "bounce off" and deal no RPG damage. With the
        // unlimited-arrow bow every shot must land.
        //
        // 1.21.8 (noDamageTicks was removed): the denial is
        //    `invulnerableTime > 10 && amount <= lastHurt` inside
        //    LivingEntity.hurtServer; setNoDamageTicks maps to
        //    invulnerableTime, and setLastDamage to lastHurt. Clearing both
        //    every tick guarantees the next hit always lands (a hit in the
        //    carry-over branch applies even while the window is open).
        vanilla.setNoDamageTicks(0);
        vanilla.setLastDamage(0);
        syncVanillaHealth();
        if (definition.getDisplayName() != null) {
            DamageUtils.updateName(vanilla);
        }
        tryCastAbility(now);
        behavior.ifPresent(b -> b.onTick(this, vanilla, now));
    }

    /**
     * Intentionally empty. The old implementation poked the vanilla entity with
     * {@code damage(0.001, ...)} so Minecraft played the hurt reaction, but that
     * reentrant hurt re-seeded {@code invulnerableTime=20} and {@code lastHurt}
     * MID-event: the outer hit's own denial check
     * ({@code invulnerableTime > 10 && amount <= lastHurt}) then always denied
     * the hit, making every projectile bounce off the mob. The outer hit's
     * allowed path already plays the hurt flash, sound and knockback on its own.
     * Never apply vanilla damage from this hook.
     */
    @Override
    protected void playHitReaction(RPGEntity attacker) {
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
        castAbility();
    }

    /**
     * Casts the equipped weapon's manual ({@code RIGHT_CLICK}) abilities.
     * Abilities that need somebody to aim at only cast while a valid target is
     * in range — {@link #findAbilityTarget()} decides whom the mob aims at
     * (a nearby visible player by default; a summon overrides it to aim at
     * enemies). Abilities that do not need a target (e.g. self-buffs) cast
     * unconditionally. Returns {@code true} if a cast actually happened.
     */
    public boolean castAbility() {
        if (getEquipmentManager().getEquippedItem(EquipmentSlot.MAIN_HAND) == null) {
            return false; // nothing castable equipped
        }
        if (castsPlayerTargetedAbility()) {
            LivingEntity target = findAbilityTarget();
            if (target == null || target.isDead() || !target.isValid()) {
                return false; // no target in sight: stop casting, resume on the next interval tick
            }
            // Face the target so the thrown projectile flies toward it.
            Location loc = vanilla.getLocation();
            loc.setDirection(target.getLocation().toVector().subtract(loc.toVector()));
            vanilla.setRotation(loc.getYaw(), loc.getPitch());
        }
        triggerAbility(AbilityAction.RIGHT_CLICK);
        return true;
    }

    /**
     * The entity this mob aims targeted abilities at: a live, in-line-of-sight
     * non-ghost player by default. Summons override this to aim at enemies of
     * the player team.
     */
    protected LivingEntity findAbilityTarget() {
        return nearestVisiblePlayer(ABILITY_CAST_RANGE);
    }

    /** Whether any of the mob's castable manual abilities needs a player target. */
    public boolean castsPlayerTargetedAbility() {
        RPGItem mainHand = getEquipmentManager().getEquippedItem(EquipmentSlot.MAIN_HAND);
        if (mainHand == null) {
            return false;
        }
        return mainHand.getAbilities().stream()
                .filter(ability -> ability.getTriggerType() == AbilityTriggerType.MANUAL
                        && ability.getAction() == AbilityAction.RIGHT_CLICK)
                .anyMatch(Ability::isTargetsPlayer);
    }

    private Player nearestVisiblePlayer(double range) {
        Player nearest = null;
        double bestSq = range * range;
        for (Player player : vanilla.getWorld().getPlayers()) {
            if (player.isDead() || !player.isOnline()
                    || EntityManager.getInstance().isGhost(player.getUniqueId())) {
                continue; // skip ghost players (dead mid-game or registered spectators)
            }
            if (StealthRegistry.shouldHideFromMob(player)) {
                continue; // orb stealth: 20% passive dodge or smoke shroud
            }
            if (!vanilla.hasLineOfSight(player)) {
                continue; // player must be in line of sight to be targeted
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

    public LivingEntity getVanilla() {
        return vanilla;
    }
}