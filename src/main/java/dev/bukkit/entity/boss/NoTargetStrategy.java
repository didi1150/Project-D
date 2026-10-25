package dev.bukkit.entity.boss;

import java.util.Optional;

import dev.core.entity.RPGEntity;
import dev.core.entity.RPGMobEntity;
import dev.core.entity.strategy.TargetingStrategy;

public class NoTargetStrategy implements TargetingStrategy {

    @Override
    public Optional<RPGEntity> selectTarget(RPGMobEntity mob) {
        return Optional.empty();
    }

}
