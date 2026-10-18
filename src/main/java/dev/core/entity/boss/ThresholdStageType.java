package dev.core.entity.boss;

import dev.core.entity.strategy.AttackPattern;
import dev.core.entity.strategy.MovementStrategy;
import dev.core.entity.strategy.TargetingStrategy;
import dev.core.storage.config.ConfigSection;

/**
 * Stage type that applies the configured movement/targeting/attack strategies
 * on enter and transitions to the next stage once the boss health drops below
 * the configured threshold.
 */
public class ThresholdStageType implements BossStageType {

    @Override
    public String getType() {
        return "THRESHOLD";
    }

    @Override
    public BossStage build(ConfigSection params, BossStrategyRegistry strategies) {
        String id = params.getString("id", "");
        double threshold = params.getDouble("health-threshold", 0.0);
        String nextStageId = params.getString("next-stage", null);

        String movementKey = params.getString("movement", "");
        MovementStrategy movement = movementKey.isBlank()
                ? null
                : strategies.movement(movementKey, params).orElse(null);

        String targetingKey = params.getString("targeting", "");
        TargetingStrategy targeting = targetingKey.isBlank()
                ? null
                : strategies.targeting(targetingKey, params).orElse(null);

        String attackKey = params.getString("attack", "");
        AttackPattern attack = attackKey.isBlank()
                ? null
                : strategies.attack(attackKey, params).orElse(null);

        return new HealthThresholdBossStage(id, threshold, nextStageId, movement, targeting, attack);
    }
}

final class HealthThresholdBossStage implements BossStage {

    private final String id;
    private final double healthPercentageThreshold;
    private final String nextStageId;
    private final MovementStrategy movementStrategy;
    private final TargetingStrategy targetingStrategy;
    private final AttackPattern attackPattern;

    HealthThresholdBossStage(String id, double healthPercentageThreshold, String nextStageId,
            MovementStrategy movementStrategy, TargetingStrategy targetingStrategy, AttackPattern attackPattern) {
        this.id = id;
        this.healthPercentageThreshold = healthPercentageThreshold;
        this.nextStageId = nextStageId;
        this.movementStrategy = movementStrategy;
        this.targetingStrategy = targetingStrategy;
        this.attackPattern = attackPattern;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void onEnter(BossStageContext context) {
        RPGBossEntity boss = context.getBoss();
        if (movementStrategy != null) {
            boss.setMovementStrategy(movementStrategy);
        }
        if (targetingStrategy != null) {
            boss.setTargetingStrategy(targetingStrategy);
        }
        if (attackPattern != null) {
            boss.setAttackPattern(attackPattern);
        }
    }

    @Override
    public boolean shouldTransition(BossStageContext context, long now) {
        RPGBossEntity boss = context.getBoss();
        double health = boss.getHealth();
        double maxHealth = boss.getMaxHealth();
        return maxHealth > 0 && (health / maxHealth) <= healthPercentageThreshold;
    }

    @Override
    public String getNextStageId(BossStageContext context) {
        return nextStageId;
    }
}
