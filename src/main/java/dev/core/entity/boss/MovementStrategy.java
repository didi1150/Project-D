package dev.core.entity.boss;

import java.util.Optional;

import dev.core.entity.RPGEntity;
import dev.core.entity.RPGMobEntity;

public interface MovementStrategy {
    void updateMovement(RPGMobEntity mob, Optional<RPGEntity> currentTarget, long now);
}
