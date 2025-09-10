package dev.core.entity;

import dev.core.stat.StatType;

public class RPGEntityAttackTracker {

	private long lastTimeAttack;
	private long lastValidAttack;

	private RPGEntity rpgEntity;

	public RPGEntityAttackTracker(RPGEntity rpgEntity) {
		this.rpgEntity = rpgEntity;
		this.lastTimeAttack = System.currentTimeMillis();
		this.lastValidAttack = System.currentTimeMillis();
	}

	public void recordSwing() {
		lastTimeAttack = System.currentTimeMillis();
	}

	public boolean canAttack() {
		long now = System.currentTimeMillis();
		long diff = now - lastValidAttack;
		double attackDelay = 1.0 / rpgEntity.getStatManager().getCurrentValue(StatType.ATTACK_SPEED, now) * 1000;
		return diff > attackDelay;
	}

	public void recordAttack() {
		lastValidAttack = System.currentTimeMillis();
	}

	public long getLastTimeAttack() {
		return lastTimeAttack;
	}

	public long getLastValidAttack() {
		return lastValidAttack;
	}

}
