package dev.core.entity.boss;

import java.util.Optional;
import java.util.UUID;

import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityType;
import dev.core.entity.RPGMobEntity;
import dev.core.event.EventBusInterface;

public class RPGBossEntity extends RPGMobEntity {

    private final BossStageManager stageManager;

    public RPGBossEntity(UUID uuid, String name, EffectManagerInterface effectManager, EventBusInterface eventBus) {
        super(uuid, name, EntityType.BOSS, effectManager, eventBus, new NoopMovementStrategy(),
                new FirstAlivePlayerTargetingStrategy(), new MeleeAttackPattern());
        this.stageManager = new BossStageManager(this);
    }

    @Override
    public void tick(long now) {
        super.tick(now);
        stageManager.tick(now);
    }

    public BossStageManager getStageManager() {
        return stageManager;
    }

    public void setInitialStage(String stageId) {
        stageManager.setInitialStage(stageId);
    }

    public Optional<BossStage> getCurrentStage() {
        return Optional.ofNullable(stageManager.getCurrentStage());
    }
}
