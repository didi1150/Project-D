package dev.core.entity.boss;

public class BossStageContext {

    private final RPGBossEntity boss;
    private final BossStageManager stageManager;

    public BossStageContext(RPGBossEntity boss, BossStageManager stageManager) {
        this.boss = boss;
        this.stageManager = stageManager;
    }

    public RPGBossEntity getBoss() {
        return boss;
    }

    public BossStageManager getStageManager() {
        return stageManager;
    }

    public long getStageStartTime() {
        return stageManager.getStageStartTime();
    }

    public FloorData getFloorData() {
        return boss.getFloorData();
    }

    public void broadcast(String message) {
        boss.getBossEntityContext().ifPresent(ctx -> ctx.broadcast(message));
    }
}
