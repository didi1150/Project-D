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

public abstract class RPGEntity {

    private final UUID uuid;
    private final String name;
    private final EntityType entityType;

    private final StatManager statManager;
    private final EquipmentManager equipmentManager;
    private final EffectManagerInterface effectManagerInterface;

    private boolean alive = true;
    private EventBusInterface eventBusInterface;
    private RPGClassType classType;
    private RPGEntityAttackTracker attackTracker;

    public RPGEntity(UUID uuid, String name, EntityType entityType, EffectManagerInterface effectManagerInterface,
            EventBusInterface eventBusInterface) {
        this(new StatManager(DefaultStats.getStatsByClass(RPGClassType.TANK)), uuid, name, entityType,
                effectManagerInterface, eventBusInterface, RPGClassType.TANK);
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
    }

    public RPGClassType getClassType() {
        return classType;
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
        setHealth(getMaxHealth());
        alive = false;
        RPGEntityDeathEvent event = new RPGEntityDeathEvent(this);
        eventBusInterface.sendEvent(event);

        EntityManager.getInstance().markDead(uuid);
    }

    public boolean onRevive() {
        EntityManager.getInstance().revive(uuid);
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
        boolean crit = false;
        double finalDamage = baseDamage;

        // Step 1: Apply critical strike (before penetration calculations)
        if (attacker != null) {
            double critChance = attacker.getStatManager().getCurrentValue(StatType.CRIT_CHANCE,
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

        return crit ? new RPGDamageResult(DamageResult.CRIT, reducedDamage)
                : new RPGDamageResult(DamageResult.NORMAL, reducedDamage);
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
        double targetArmor = target.getStatManager().getCurrentValue(StatType.ARMOR, System.currentTimeMillis());
        double effectiveArmor = targetArmor;

        if (attacker != null) {
            // Step 1: Apply Lethality (flat armor reduction)
            double lethality = attacker.getStatManager().getCurrentValue(StatType.LETHALITY,
                    System.currentTimeMillis());
            effectiveArmor = Math.max(0, effectiveArmor - lethality);

            // Step 2: Apply Armor Penetration (percentage)
            double armorPen = attacker.getStatManager().getCurrentValue(StatType.ARMOR_PENETRATION,
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
        double targetMR = target.getStatManager().getCurrentValue(StatType.MAGIC_RESIST, System.currentTimeMillis());
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
        double healPower = target.getStatManager().getCurrentValue(StatType.HEAL_AND_SHIELD_POWER,
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

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    // =========================- Convenience ==========================

    public double getHealth() {
        return statManager.getCurrentValue(StatType.HEALTH_RESOURCE, System.currentTimeMillis());
    }

    public double getMaxHealth() {
        return statManager.getCurrentValue(StatType.HEALTH_MAX, System.currentTimeMillis());
    }

    public double getMana() {
        return statManager.getCurrentValue(StatType.MANA_RESOURCE, System.currentTimeMillis());
    }

    public double getMaxMana() {
        return statManager.getCurrentValue(StatType.MANA_MAX, System.currentTimeMillis());
    }

    public void setHealth(double value) {
        statManager.setCurrentValue(StatType.HEALTH_RESOURCE, value);
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
