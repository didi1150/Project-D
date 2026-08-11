package dev.core.entity.boss;

import java.util.Optional;
import java.util.UUID;

import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityType;
import dev.core.entity.RPGMobEntity;
import dev.core.event.EventBusInterface;
import dev.core.game.TaskScheduler;

public class RPGBossEntity extends RPGMobEntity {

    private final BossStageManager stageManager;
    private BossEntityContext bossEntityContext;
    private String defeatStageId;
    private boolean defeatTriggered;

    public RPGBossEntity(UUID uuid, String name, EffectManagerInterface effectManager, EventBusInterface eventBus,
            TaskScheduler scheduler) {
        super(uuid, name, EntityType.BOSS, effectManager, eventBus, new NoopMovementStrategy(),
                new FirstAlivePlayerTargetingStrategy(), new MeleeAttackPattern());
        this.stageManager = new BossStageManager(this, scheduler);
    }

    /**
     * Applies a definition to this boss instance: stats, stages and the defeat
     * stage. Must be called before the boss is ticked.
     */
    public void configure(BossDefinition definition, BossEntityContext context) {
        this.bossEntityContext = context;
        this.defeatStageId = definition.getDefeatStageId();
        getStatManager().addAll(definition.getBaseStatManager().getStats());
        for (BossStage stage : definition.getStages()) {
            stageManager.addStage(stage);
        }
        if (definition.getStages().isEmpty()) {
            this.defeatStageId = null;
        } else {
            stageManager.setInitialStage(definition.getStages().get(0).getId());
        }
    }

    @Override
    public void tick(long now) {
        if (isDefeatSequenceActive()) {
            stageManager.tick(now);
            return;
        }
        if (isAlive() && !defeatTriggered && getHealth() <= 0 && defeatStageId != null && !defeatStageId.isBlank()) {
            defeatTriggered = true;
            stageManager.transitionTo(defeatStageId, now);
            return;
        }
        super.tick(now);
        stageManager.tick(now);
    }

    /**
     * True while the boss is at 0 health but the defeat stage (e.g. a monologue)
     * is still running.
     */
    public boolean isDefeatSequenceActive() {
        return defeatTriggered && isAlive();
    }

    @Override
    public void onDeath() {
        if (!isAlive()) {
            return;
        }
        if (defeatTriggered) {
            return;
        }
        if (defeatStageId != null && !defeatStageId.isBlank()) {
            defeatTriggered = true;
            stageManager.transitionTo(defeatStageId, System.currentTimeMillis());
            return;
        }
        super.onDeath();
    }

    /**
     * Finishes the defeat sequence: fires the death event, marks the entity dead
     * and lets the platform layer clean up the underlying entity.
     */
    public void completeDefeat() {
        if (!defeatTriggered) {
            return;
        }
        super.onDeath();
        onDefeatFinished();
    }

    /**
     * Hook for the platform layer to clean up after the defeat is complete.
     */
    protected void onDefeatFinished() {
    }

    /**
     * Hook fired whenever the current stage changes (including the initial
     * stage).
     */
    protected void onStageTransition(BossStage nextStage) {
    }

    public Optional<BossEntityContext> getBossEntityContext() {
        return Optional.ofNullable(bossEntityContext);
    }

    public String getDefeatStageId() {
        return defeatStageId;
    }

    public BossStageManager getStageManager() {
        return stageManager;
    }

    public Optional<BossStage> getCurrentStage() {
        return Optional.ofNullable(stageManager.getCurrentStage());
    }
}
