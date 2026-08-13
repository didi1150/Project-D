package dev.core.entity.boss;

import java.util.Optional;

import dev.core.entity.EntityManager;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.RPGMobEntity;

public class FirstAlivePlayerTargetingStrategy implements TargetingStrategy {

    @Override
    public Optional<RPGEntity> selectTarget(RPGMobEntity mob) {
        return EntityManager.getInstance().getAliveEntities().stream()
                .filter(entity -> entity != mob && entity.getEntityType() == EntityType.PLAYER && entity.isAlive())
                .findFirst();
    }
}
