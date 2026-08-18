package dev.core.entity;

import java.util.Optional;
import java.util.UUID;

import dev.core.ability.EffectManagerInterface;
import dev.core.entity.boss.AttackPattern;
import dev.core.entity.boss.FirstAlivePlayerTargetingStrategy;
import dev.core.entity.boss.MeleeAttackPattern;
import dev.core.entity.boss.MovementStrategy;
import dev.core.entity.boss.NoopMovementStrategy;
import dev.core.entity.boss.TargetingStrategy;
import dev.core.event.EventBusInterface;
import dev.core.status.StatusEffectManagerInterface;

public class RPGMobEntity extends RPGEntity {

    private MovementStrategy movementStrategy;
    private TargetingStrategy targetingStrategy;
    private AttackPattern attackPattern;
    private Optional<RPGEntity> currentTarget = Optional.empty();

    public RPGMobEntity(UUID uuid, String name, EffectManagerInterface effectManager,
            EventBusInterface eventBus) {
        this(uuid, name, EntityType.MOB, effectManager, eventBus, new NoopMovementStrategy(),
                new FirstAlivePlayerTargetingStrategy(), new MeleeAttackPattern());
    }

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
