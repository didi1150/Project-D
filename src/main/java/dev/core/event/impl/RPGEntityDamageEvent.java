package dev.core.event.impl;

import dev.core.entity.RPGEntity;

public class RPGEntityDamageEvent extends RPGEntityModifyHealthEvent {

	private final DamageType damageType;

	public RPGEntityDamageEvent(RPGEntity source, RPGEntity target, double amount, DamageType type) {
		super(source, target, amount);
		this.damageType = type;
	}

	public DamageType getDamageType() {
		return damageType;
	}

	public enum DamageType {
		PHYSICAL, MAGIC, TRUE;
	}
	
	public enum DamageResult {
		NORMAL, DENY, CRIT;
	}

}
