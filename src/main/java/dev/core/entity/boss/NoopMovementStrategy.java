package dev.core.entity.boss;

import java.util.Optional;

import dev.core.entity.RPGEntity;
import dev.core.entity.RPGMobEntity;

public class NoopMovementStrategy implements MovementStrategy {

    @Override
    public void updateMovement(RPGMobEntity mob, Optional<RPGEntity> currentTarget, long now) {
        // Default mobs do not enforce movement in core. Bukkit or other wrappers may implement actual pathing.
    }
}
