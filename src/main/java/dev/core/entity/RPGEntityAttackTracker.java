package dev.core.entity;

import dev.core.stat.StatType;

public class RPGEntityAttackTracker {

    private long lastTimeAttack;

    private RPGEntity rpgEntity;

    public RPGEntityAttackTracker(RPGEntity rpgEntity) {
        this.rpgEntity = rpgEntity;
        this.lastTimeAttack = System.currentTimeMillis();
    }

    public void recordSwing() {
        lastTimeAttack = System.currentTimeMillis();
    }

    public boolean canAttack() {
        long now = System.currentTimeMillis();
        long diff = now - lastTimeAttack;

        return diff > rpgEntity.getStatManager().getCurrentValue(StatType.ATTACK_SPEED, now);
    }

}
