package dev.core.entity;

import java.util.UUID;

import dev.core.item.EquipmentManager;
import dev.core.stat.StatManager;
import dev.core.stat.effect.EffectManager;

public abstract class RPGEntity {

	private StatManager statManager;

	private EffectManager effectManager;

	private EquipmentManager equipmentManager;

	private UUID uuid;

	private String name;

	private EntityType entityType;

	public RPGEntity(StatManager statManager, UUID uuid, String name, EntityType entityType) {
		this.statManager = statManager;
		this.effectManager = new EffectManager(this);
		this.equipmentManager = new EquipmentManager();
		this.uuid = uuid;
		this.name = name;
		this.entityType = entityType;
	}

}
