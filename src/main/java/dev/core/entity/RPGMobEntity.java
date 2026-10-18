package dev.core.entity;

import java.util.Optional;
import java.util.UUID;

import dev.core.ability.EffectManagerInterface;
import dev.core.entity.strategy.AttackPattern;
import dev.core.entity.strategy.MovementStrategy;
import dev.core.entity.strategy.TargetingStrategy;
import dev.core.event.EventBusInterface;
import dev.core.status.StatusEffectManagerInterface;

public class RPGMobEntity extends RPGEntity {

    private MovementStrategy movementStrategy;
    private TargetingStrategy targetingStrategy;
    private AttackPattern attackPattern;
    private Optional<RPGEntity> currentTarget = Optional.empty();

    public RPGMobEntity(UUID uuid, String name, EntityType entityType, EffectManagerInterface effectManager,
            EventBusInterface eventBus, MovementStrategy movementStrategy, TargetingStrategy targetingStrategy,
            AttackPattern attackPattern) {
        super(uuid, name, entityType, effectManager, eventBus);
        this.movementStrategy = movementStrategy;
        this.targetingStrategy = targetingStrategy;
        this.attackPattern = attackPattern;
    }

    public RPGMobEntity(UUID uuid, String name, EntityType entityType, EffectManagerInterface effectManager,
            EventBusInterface eventBus, MovementStrategy movementStrategy, TargetingStrategy targetingStrategy,
            AttackPattern attackPattern, StatusEffectManagerInterface statusEffects) {
        super(uuid, name, entityType, effectManager, eventBus, statusEffects);
        this.movementStrategy = movementStrategy;
        this.targetingStrategy = targetingStrategy;
        this.attackPattern = attackPattern;
    }

    @Override
    public void tick(long now) {
        super.tick(now);
        if (!isAlive()) {
            return;
        }

        currentTarget = targetingStrategy.selectTarget(this);
        movementStrategy.updateMovement(this, currentTarget, now);
        attackPattern.performAttack(this, currentTarget, now);
    }

    public Optional<RPGEntity> getCurrentTarget() {
        return currentTarget;
    }

    public void setMovementStrategy(MovementStrategy movementStrategy) {
        this.movementStrategy = movementStrategy;
    }

    public void setTargetingStrategy(TargetingStrategy targetingStrategy) {
        this.targetingStrategy = targetingStrategy;
    }

    public void setAttackPattern(AttackPattern attackPattern) {
        this.attackPattern = attackPattern;
    }
}
