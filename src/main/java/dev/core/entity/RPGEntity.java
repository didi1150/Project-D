package dev.core.entity;

import java.util.Random;
import java.util.UUID;

import dev.core.ability.AbilityAction;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.event.impl.RPGEntityDamageEvent;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.event.impl.RPGEntityDeathEvent;
import dev.core.event.impl.RPGEntityHealEvent;
import dev.core.event.impl.RPGEntityHealEvent.HealReason;
import dev.core.item.equipment.EquipmentManager;
import dev.core.stat.DefaultStats;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.engine.StatEngine;
import dev.core.stat.engine.StatEngineAdapter;
import dev.core.stat.provider.adapter.ModifierBucketProvider;

public abstract class RPGEntity {

    private final UUID uuid;
    private final String name;
    private final EntityType entityType;

    private final StatManager statManager;
    private final EquipmentManager equipmentManager;
    private final EffectManagerInterface effectManagerInterface;
    private final StatEngine statEngine;
    private final StatEngineAdapter statEngineAdapter;

    private boolean alive = true;
    private boolean damageImmune = false;
    private double abilityDamageMultiplier = 1.0;
    private EventBusInterface eventBusInterface;
    private RPGClassType classType;
    private RPGEntityAttackTracker attackTracker;

    public RPGEntity(UUID uuid, String name, EntityType entityType, EffectManagerInterface effectManagerInterface,
            EventBusInterface eventBusInterface) {
        this(new StatManager(DefaultStats.getDefaultStats()), uuid, name, entityType, effectManagerInterface,
                eventBusInterface, RPGClassType.NONE);
    }

    public RPGEntity(StatManager statManager, UUID uuid, String name, EntityType entityType,
            EffectManagerInterface effectManagerInterface, EventBusInterface eventBusInterface,
            RPGClassType classType) {
        this.statManager = statManager;
        this.effectManagerInterface = effectManagerInterface;
        this.eventBusInterface = eventBusInterface;
        this.equipmentManager = new EquipmentManager(this, eventBusInterface, effectManagerInterface);
        this.uuid = uuid;
        this.name = name;
        this.entityType = entityType;
        this.classType = classType;
        this.attackTracker = new RPGEntityAttackTracker(this);
        this.statEngine = new StatEngine(this);
        // register provider that bridges the legacy StatManager modifier buckets
        this.statEngine.registerProvider(new ModifierBucketProvider(this));
        // let StatManager know about the new engine so it can invalidate caches on
        // change
        this.statManager.setStatEngine(this.statEngine);
        // create adapter for migrating combat methods
        this.statEngineAdapter = new StatEngineAdapter(this.statEngine, this.statManager);
    }

    public RPGClassType getClassType() {
        return classType;
    }

    /**
     * Create a builder for constructing RPGEntity instances. Provides fluent API
     * for flexible entity configuration.
     */
    public static RPGEntityBuilder builder(UUID uuid, String name, EntityType entityType) {
        return new RPGEntityBuilder(uuid, name, entityType);
    }

    // =========================- Lifecycle ==========================

    public void tick(long now) {
        statManager.tick(now);
        equipmentManager.tick(now);
        checkAlive();
    }

    private void checkAlive() {
        if (getHealth() <= 0 && alive) {
            onDeath();
        }
    }

    public void onDeath() {
        alive = false;
        setHealth(getMaxHealth());
        RPGEntityDeathEvent event = new RPGEntityDeathEvent(this);
        eventBusInterface.sendEvent(event);

        EntityManager.getInstance().markDead(uuid);
    }

    /**
     * Implementation hook invoked by {@link EntityManager#revive(UUID)} when an
     * entity is revived from the dead state (it was marked dead, as opposed to
     * merely being a ghosted player such as a spectator). The manager has already
     * marked the entity alive again; implementations restore whatever state was
     * lost on death (e.g. a player's saved inventory).
     */
    public boolean onRevive() {
        return true;
    }

    public void recordSwing() {
        attackTracker.recordSwing();
    }

    public void recordAttack() {
        attackTracker.recordAttack();
    }

    public boolean canAttack() {
        return attackTracker.canAttack();
    }

    public long getLastValidAttack() {
        return attackTracker.getLastValidAttack();
    }
    // =========================- Combat ==========================

//    public RPGDamageResult dealRPGDamage(RPGEntity attacker, RPGEntity target, double baseDamage,
//            DamageType damageType) {
//        boolean crit = false;
//        if (attacker != null) {
//            double critChance = attacker.getStatManager().getCurrentValue(StatType.CRIT_CHANCE,
//                    System.currentTimeMillis());
//            int roll = new Random().nextInt(101);
//            if (roll < critChance) {
//                baseDamage = baseDamage * 1.75;
//                crit = true;
//            }
//        }
//        // Step 1: Fire event
//        RPGEntityDamageEvent event = new RPGEntityDamageEvent(attacker, target, baseDamage, damageType);
//        eventBusInterface.sendEvent(event);
//
//        // Step 2: Check cancel
//        if (event.isCancelled()) {
//            return new RPGDamageResult(DamageResult.DENY, 0);
//        }
//
//        // Step 3: Apply defenses
//        double reduced = applyDefense(event.getAmount(), event.getDamageType(), target);
//
//        // Step 4: Apply to target
//        target.setHealth(target.getHealth() - reduced);
//        checkAlive();
//        return crit ? new RPGDamageResult(DamageResult.CRIT, baseDamage)
//                : new RPGDamageResult(DamageResult.NORMAL, baseDamage);
////	    eventBusInterface.sendEvent(new RPGDamageAppliedEvent(attacker, target, reduced));
//    }
//
//    private double applyDefense(double amount, DamageType damageType, RPGEntity target) {
//        if (damageType == DamageType.PHYSICAL) {
//            double armour = target.getStatManager().getCurrentValue(StatType.ARMOR, System.currentTimeMillis());
//            double multiplier = 100.0 / (100.0 + armour);
//            return amount * multiplier;
//        } else if (damageType == DamageType.MAGIC) {
//            double mr = target.getStatManager().getCurrentValue(StatType.MAGIC_RESIST, System.currentTimeMillis());
//            double multiplier = 100.0 / (100.0 + mr);
//            return amount * multiplier;
//        }
//        return amount;
//    }

    public RPGDamageResult dealRPGDamage(RPGEntity attacker, RPGEntity target, double baseDamage,
            DamageType damageType) {
        // Step 0: Damage immunity (e.g. a boss stage that turns it on). Immune
        // targets take no damage and get no hurt reaction.
        if (target.isDamageImmune()) {
            return new RPGDamageResult(DamageResult.DENY, 0);
        }

        // Ghosts (dead players kept registered) and other dead entities take no
        // damage: no damage, no hurt animation, no damage indicator.
        if (!target.isAlive()) {
            return new RPGDamageResult(DamageResult.DENY, 0);
        }

        // No PvP: players must never damage each other through the RPG pipeline
        // (ability AoE, bonemerang, spirit sceptre, melee translation, ...).
        if (attacker != null && attacker.getEntityType() == EntityType.PLAYER
                && target.getEntityType() == EntityType.PLAYER) {
            return new RPGDamageResult(DamageResult.DENY, 0);
        }

        boolean crit = false;
        double finalDamage = baseDamage;

        // Step 1: Apply critical strike (before penetration calculations)
        if (attacker != null) {
            double critChance = attacker.getStatEngineAdapter().getCurrentValue(StatType.CRIT_CHANCE,
                    System.currentTimeMillis());
            int roll = new Random().nextInt(101);
            if (roll < critChance) {
                finalDamage = baseDamage * 1.75;
                crit = true;
            }
        }

        // Step 2: Fire event with post-crit damage
        RPGEntityDamageEvent event = new RPGEntityDamageEvent(attacker, target, finalDamage, damageType);
        eventBusInterface.sendEvent(event);

        // Step 3: Check cancel
        if (event.isCancelled()) {
            return new RPGDamageResult(DamageResult.DENY, 0);
        }

        // Step 4: Apply penetration and defenses (LoL system)
        double reducedDamage = applyAllDefenses(event.getAmount(), event.getDamageType(), attacker, target);

        // Step 5: Apply to target
        target.setHealth(target.getHealth() - reducedDamage);
        target.checkAlive();

        // Step 6: Central hurt reaction (flash + sound + small knockback) on the
        // victim. Only runs for a landed hit on a non-immune target — immune
        // targets returned DENY at step 0. Bukkit subclasses override
        // playHitReaction(); the core default does nothing.
        if (reducedDamage > 0) {
            target.playHitReaction(attacker);
        }

        return crit ? new RPGDamageResult(DamageResult.CRIT, reducedDamage)
                : new RPGDamageResult(DamageResult.NORMAL, reducedDamage);
    }

    /**
     * Hook for the vanilla hurt reaction after a landed RPG hit. Overridden by
     * Bukkit subclasses to {@code damage()} the backing entity with a negligible
     * amount so Minecraft plays the full hit reaction (hurt flash + sound + slight
     * knockback). Only called when the target is not immune.
     */
    protected void playHitReaction(RPGEntity attacker) {
    }

    private double applyAllDefenses(double damage, DamageType damageType, RPGEntity attacker, RPGEntity target) {
        if (damageType == DamageType.PHYSICAL) {
            return applyPhysicalDefense(damage, attacker, target);
        } else if (damageType == DamageType.MAGIC) {
            return applyMagicDefense(damage, attacker, target);
        }
        return damage; // True damage bypasses all defenses
    }

    private double applyPhysicalDefense(double damage, RPGEntity attacker, RPGEntity target) {
        double targetArmor = target.getStatEngineAdapter().getCurrentValue(StatType.ARMOR, System.currentTimeMillis());
        double effectiveArmor = targetArmor;

        if (attacker != null) {
            // Step 1: Apply Lethality (flat armor reduction)
            double lethality = attacker.getStatEngineAdapter().getCurrentValue(StatType.LETHALITY,
                    System.currentTimeMillis());
            effectiveArmor = Math.max(0, effectiveArmor - lethality);

            // Step 2: Apply Armor Penetration (percentage)
            double armorPen = attacker.getStatEngineAdapter().getCurrentValue(StatType.ARMOR_PENETRATION,
                    System.currentTimeMillis());
            effectiveArmor = effectiveArmor * (1.0 - (armorPen / 100.0));
        }

        // Step 3: Calculate damage reduction
        double damageMultiplier;
        if (effectiveArmor >= 0) {
            damageMultiplier = 100.0 / (100.0 + effectiveArmor);
        } else {
            // Negative armor increases damage
            damageMultiplier = 2.0 - (100.0 / (100.0 - effectiveArmor));
        }

        return damage * damageMultiplier;
    }

    private double applyMagicDefense(double damage, RPGEntity attacker, RPGEntity target) {
        double targetMR = target.getStatEngineAdapter().getCurrentValue(StatType.MAGIC_RESIST,
                System.currentTimeMillis());
        double effectiveMR = targetMR;

        // Calculate damage reduction without penetration
        double damageMultiplier;
        if (effectiveMR >= 0) {
            damageMultiplier = 100.0 / (100.0 + effectiveMR);
        } else {
            // Negative MR increases damage
            damageMultiplier = 2.0 - (100.0 / (100.0 - effectiveMR));
        }

        return damage * damageMultiplier;
    }

    public void healRPGEntity(RPGEntity healer, RPGEntity target, double baseHeal, HealReason healReason) {
        RPGEntityHealEvent event = new RPGEntityHealEvent(healer, target, baseHeal, healReason);
        eventBusInterface.sendEvent(event);

        if (event.isCancelled())
            return;

        double boosted = applyHealPower(event.getAmount(), target);

        target.setHealth(Math.min(target.getMaxHealth(), target.getHealth() + boosted));

//		eventBusInterface.sendEvent(new RPGHealAppliedEvent(healer, target, boosted));
    }

    private double applyHealPower(double amount, RPGEntity target) {
        double healPower = target.getStatEngineAdapter().getCurrentValue(StatType.HEAL_AND_SHIELD_POWER,
                System.currentTimeMillis());

        return amount * (1.0 + (healPower / 100.0));
    }

    // =========================- Abilities ==========================

    public void triggerAbility(AbilityAction abilityAction) {
        equipmentManager.triggerAbility(abilityAction);
    }

    // =========================- Getters ==========================

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public StatManager getStatManager() {
        return statManager;
    }

    public EquipmentManager getEquipmentManager() {
        return equipmentManager;
    }

    public EffectManagerInterface getEffectManager() {
        return effectManagerInterface;
    }

    public StatEngine getStatEngine() {
        return statEngine;
    }

    public StatEngineAdapter getStatEngineAdapter() {
        return statEngineAdapter;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    // =========================- Convenience ==========================

    public double getHealth() {
        return getStatEngineAdapter().getCurrentValue(StatType.HEALTH_RESOURCE, System.currentTimeMillis());
    }

    public double getMaxHealth() {
        return getStatEngineAdapter().getCurrentValue(StatType.HEALTH_MAX, System.currentTimeMillis());
    }

    /**
     * Multiplier applied to projectile damage this entity deals (arrows, other
     * thrown projectiles and the bonemerang). Derived from the
     * {@link StatType#PROJECTILE_DAMAGE} stat (a percent: 10 = +10%). Set bonuses
     * such as the Basic Archer Set raise it; 1.0 = no bonus.
     */
    public double getProjectileDamageMultiplier() {
        return 1.0 + getStatEngineAdapter().getCurrentValue(StatType.PROJECTILE_DAMAGE, System.currentTimeMillis())
                / 100.0;
    }

    public double getMana() {
        return getStatEngineAdapter().getCurrentValue(StatType.MANA_RESOURCE, System.currentTimeMillis());
    }

    public double getMaxMana() {
        return getStatEngineAdapter().getCurrentValue(StatType.MANA_MAX, System.currentTimeMillis());
    }

    public void setHealth(double value) {
        statManager.setCurrentValue(StatType.HEALTH_RESOURCE, value);
    }

    /**
     * Whether this entity currently ignores all {@link #dealRPGDamage}. Boss stages
     * can toggle this to make the boss invulnerable to damage (and to the hurt
     * reaction) during a phase.
     */
    public boolean isDamageImmune() {
        return damageImmune;
    }

    public void setDamageImmune(boolean damageImmune) {
        this.damageImmune = damageImmune;
    }

    /**
     * Multiplier applied to the damage this entity's abilities deal (e.g. a mob's
     * reduced bone damage). Effects read it at cast/tick time; default 1.0.
     */
    public double getAbilityDamageMultiplier() {
        return abilityDamageMultiplier;
    }

    public void setAbilityDamageMultiplier(double abilityDamageMultiplier) {
        this.abilityDamageMultiplier = abilityDamageMultiplier;
    }

    public void setMana(double value) {
        statManager.setCurrentValue(StatType.MANA_RESOURCE, value);
    }

    public void addStatModifier(StatModifier mod) {
        statManager.addStatModifier(mod);
    }

    public void removeStatModifier(StatModifier mod) {
        statManager.removeStatModifier(mod);
    }

}
