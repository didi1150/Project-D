package dev.core.entity.boss;

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
