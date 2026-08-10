package dev.core.entity.boss;

import java.util.Optional;

import dev.core.entity.RPGEntity;
import dev.core.entity.RPGMobEntity;

public interface TargetingStrategy {
    Optional<RPGEntity> selectTarget(RPGMobEntity mob);
}
