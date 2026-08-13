package dev.core.entity.boss;

public interface BossStage {
    String getId();

    default void onEnter(BossStageContext context) {
    }

    default void onExit(BossStageContext context) {
    }

    default void tick(BossStageContext context, long now) {
    }

    default boolean shouldTransition(BossStageContext context, long now) {
        return false;
    }

    default String getNextStageId(BossStageContext context) {
        return null;
    }
}
