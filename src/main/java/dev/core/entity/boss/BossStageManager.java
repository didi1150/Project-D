package dev.core.entity.boss;

import java.util.HashMap;
import java.util.Map;

import dev.bukkit.DMain;
import dev.bukkit.game.scheduler.BukkitTaskScheduler;
import dev.core.game.TaskScheduler;

public class BossStageManager {

    private final RPGBossEntity boss;
    private final Map<String, BossStage> stages = new HashMap<>();
    private BossStage currentStage;
    private BossStageContext context;
    private long stageStartTime;
    private TaskScheduler scheduler;

    public BossStageManager(RPGBossEntity boss) {
        this.boss = boss;
        this.context = new BossStageContext(boss, this);
        scheduler = new BukkitTaskScheduler(DMain.getInstance());
    }

    public void addStage(BossStage stage) {
        stages.put(stage.getId(), stage);
    }

    public void setInitialStage(String stageId) {
        BossStage stage = stages.get(stageId);
        if (stage == null) {
            throw new IllegalArgumentException("Boss stage not found: " + stageId);
        }
        currentStage = stage;
        stageStartTime = System.currentTimeMillis();
        currentStage.onEnter(context);
    }

    public void tick(long now) {
        if (currentStage == null || !boss.isAlive()) {
            return;
        }

        currentStage.tick(context, now);

        if (currentStage.shouldTransition(context, now)) {
            String nextStageId = currentStage.getNextStageId(context);
            if (nextStageId != null && !nextStageId.isEmpty()) {
                transitionTo(nextStageId, now);
            }
        }
    }

    public void transitionTo(String stageId, long now) {
        if (currentStage != null) {
            currentStage.onExit(context);
        }

        BossStage nextStage = stages.get(stageId);
        if (nextStage == null) {
            throw new IllegalArgumentException("Boss stage not found: " + stageId);
        }

        currentStage = nextStage;
        stageStartTime = now;
        context.setState("lastStageChangeTime", now);
        currentStage.onEnter(context);
    }

    public BossStage getCurrentStage() {
        return currentStage;
    }

    public long getStageStartTime() {
        return stageStartTime;
    }

    public BossStageContext getContext() {
        return context;
    }

    public TaskScheduler getScheduler() {
        return scheduler;
    }

}
