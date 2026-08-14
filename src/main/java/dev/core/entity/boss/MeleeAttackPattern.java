package dev.core.entity.boss;

import java.util.Optional;

import dev.core.entity.RPGEntity;
import dev.core.entity.RPGMobEntity;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.StatType;

public class MeleeAttackPattern implements AttackPattern {

    @Override
    public void performAttack(RPGMobEntity mob, Optional<RPGEntity> currentTarget, long now) {
        if (currentTarget.isEmpty()) {
            return;
        }

        RPGEntity target = currentTarget.get();
        if (!mob.canAttack()) {
            return;
        }

        double baseDamage = mob.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now);
        mob.dealRPGDamage(mob, target, baseDamage, DamageType.PHYSICAL);
        mob.recordAttack();
    }
}
