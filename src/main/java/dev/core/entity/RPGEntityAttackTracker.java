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
		double attackSpeed = rpgEntity.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_SPEED, now);
		// A missing or zero ATTACK_SPEED would make the delay infinite and lock
		// the entity out of attacking forever; treat it as "no cooldown".
		if (!Double.isFinite(attackSpeed) || attackSpeed <= 0) {
			return true;
		}
		double attackDelay = 1000.0 / attackSpeed;
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
