package dev.core.entity;

import java.util.Random;
import java.util.UUID;

import dev.core.ability.AbilityAction;
import dev.core.ability.EffectManagerInterface;
import dev.core.event.EventBusInterface;
import dev.core.event.impl.RPGEntityDamageEvent;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.event.impl.RPGEntityDeathEvent;
import dev.core.event.impl.RPGEntityHealEvent;
import dev.core.event.impl.RPGEntityHealEvent.HealReason;
import dev.core.item.EquipmentManager;
import dev.core.stat.StatManager;
import dev.core.stat.StatModifier;
import dev.core.stat.StatType;

public abstract class RPGEntity {

	private final UUID uuid;
	private final String name;
	private final EntityType entityType;

	private final StatManager statManager;
	private final EquipmentManager equipmentManager;
	private final EffectManagerInterface effectManagerInterface;

	private boolean alive = true;
	private EventBusInterface eventBusInterface;
	private int lastMainHandSlot = 0;

	public RPGEntity(StatManager statManager, UUID uuid, String name, EntityType entityType,
			EffectManagerInterface effectManagerInterface, EventBusInterface eventBusInterface) {
		this.statManager = statManager;
		this.effectManagerInterface = effectManagerInterface;
		this.eventBusInterface = eventBusInterface;
		this.equipmentManager = new EquipmentManager(this, eventBusInterface, effectManagerInterface);
		this.uuid = uuid;
		this.name = name;
		this.entityType = entityType;
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
		setHealth(0);
		alive = false;
		RPGEntityDeathEvent event = new RPGEntityDeathEvent(this);
		eventBusInterface.sendEvent(event);

		EntityManager.getInstance().markDead(uuid);
	}

	// =========================- Combat ==========================

	public RPGDamageResult dealRPGDamage(RPGEntity attacker, RPGEntity target, double baseDamage,
			DamageType damageType) {
		boolean crit = false;
		if (attacker != null) {
			double critChance = attacker.getStatManager().getCurrentValue(StatType.CRIT_CHANCE,
					System.currentTimeMillis());
			int roll = new Random().nextInt(101);
			if (roll < critChance) {
				baseDamage = baseDamage * 1.75;
				crit = true;
			}
		}
		// Step 1: Fire event
		RPGEntityDamageEvent event = new RPGEntityDamageEvent(attacker, target, baseDamage, damageType);
		eventBusInterface.sendEvent(event);

		// Step 2: Check cancel
		if (event.isCancelled()) {
			return new RPGDamageResult(DamageResult.DENY, 0);
		}

		// Step 3: Apply defenses
		double reduced = applyDefense(event.getAmount(), event.getDamageType(), target);

		// Step 4: Apply to target
		target.setHealth(target.getHealth() - reduced);
		checkAlive();
		return crit ? new RPGDamageResult(DamageResult.CRIT, baseDamage)
				: new RPGDamageResult(DamageResult.NORMAL, baseDamage);
//	    eventBusInterface.sendEvent(new RPGDamageAppliedEvent(attacker, target, reduced));
	}

	private double applyDefense(double amount, DamageType damageType, RPGEntity target) {
		if (damageType == DamageType.PHYSICAL) {
			double armour = target.getStatManager().getCurrentValue(StatType.ARMOR, System.currentTimeMillis());
			double multiplier = 100.0 / (100.0 + armour);
			return amount * multiplier;
		} else if (damageType == DamageType.MAGIC) {
			double mr = target.getStatManager().getCurrentValue(StatType.MAGIC_RESIST, System.currentTimeMillis());
			double multiplier = 100.0 / (100.0 + mr);
			return amount * multiplier;
		}
		return amount;
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
