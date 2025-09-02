package dev.core.entity;

import java.util.UUID;

import dev.core.ability.AbilityAction;
import dev.core.ability.EffectManagerInterface;
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

	public RPGEntity(StatManager statManager, UUID uuid, String name, EntityType entityType,
			EffectManagerInterface effectManagerInterface) {
		this.statManager = statManager;
		this.effectManagerInterface = effectManagerInterface;
		this.equipmentManager = new EquipmentManager(this);
		this.uuid = uuid;
		this.name = name;
		this.entityType = entityType;
	}

	// =========================- Lifecycle ==========================

	public void tick(long now) {
		statManager.tick(now);
		effectManagerInterface.tick(now);
		equipmentManager.tick(now);
		checkAlive(now);
	}

	private void checkAlive(long now) {
		if (getHealth(now) <= 0 && alive) {
			alive = false;
			onDeath();
		}
	}

	protected void onDeath() {
		// Hook for subclasses
		// TODO: Call death event
	}

	// =========================- Combat ==========================

	public void damage(double amount, long now) {
		statManager.modifyStat(StatType.HEALTH_RESOURCE, -amount);
		checkAlive(now);
		// TODO: Notify GameContext / trigger event
	}

	public void damageByEntity(double amount, RPGEntity source, long now) {
		damage(amount, now);
		// TODO: trigger "damagedByEntity" event with source
	}

	public void heal(double amount) {
		statManager.modifyStat(StatType.HEALTH_RESOURCE, amount);
		// TODO: Notify GameContext / trigger event
	}

	// =========================- Abilities ==========================

	public void triggerAbility(AbilityAction abilityAction) {
		equipmentManager.triggerAbility(abilityAction, effectManagerInterface);
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

	// =========================- Convenience ==========================

	public double getHealth(long now) {
		return statManager.getCurrentValue(StatType.HEALTH_RESOURCE, now);
	}

	public double getMaxHealth(long now) {
		return statManager.getCurrentValue(StatType.HEALTH_MAX, now);
	}

	public void addStatModifier(StatModifier mod) {
		statManager.addStatModifier(mod);
	}

	public void removeStatModifier(StatModifier mod) {
		statManager.removeStatModifier(mod);
	}
}
