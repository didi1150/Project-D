package dev.core.entity.boss;

import java.util.Optional;

import dev.core.entity.RPGEntity;
import dev.core.entity.RPGMobEntity;

public interface AttackPattern {
    void performAttack(RPGMobEntity mob, Optional<RPGEntity> currentTarget, long now);
}
