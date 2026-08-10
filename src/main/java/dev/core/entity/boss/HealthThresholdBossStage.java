package dev.core.entity.boss;

public class HealthThresholdBossStage implements BossStage {

    private final String id;
    private final double healthPercentageThreshold;
    private final String nextStageId;
    private final MovementStrategy movementStrategy;
    private final TargetingStrategy targetingStrategy;
    private final AttackPattern attackPattern;

    public HealthThresholdBossStage(String id, double healthPercentageThreshold, String nextStageId,
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
