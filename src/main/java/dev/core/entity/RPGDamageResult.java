package dev.core.entity;

import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;

public class RPGDamageResult {

	private DamageResult result;
	private double damage;

	public RPGDamageResult(DamageResult result, double damage) {
		this.result = result;
		this.damage = damage;
	}

	public double getDamage() {
		return damage;
	}

	public DamageResult getResult() {
		return result;
	}

}
