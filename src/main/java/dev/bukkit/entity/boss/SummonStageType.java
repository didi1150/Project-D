package dev.bukkit.entity.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import dev.core.entity.boss.BossStage;
import dev.core.entity.boss.BossStageContext;
import dev.core.entity.boss.BossStageType;
import dev.core.entity.boss.BossStrategyRegistry;
import dev.core.game.ScheduledTask;
import dev.core.storage.config.ConfigSection;

/**
 * Stage type that periodically spawns minions near the boss for a fixed
 * duration, then transitions to the next stage.
 */
public class SummonStageType implements BossStageType {

    @Override
    public String getType() {
        return "SUMMON";
    }

    @Override
    public BossStage build(ConfigSection params, BossStrategyRegistry strategies) {
        String id = params.getString("id", "");
        String summonEntity = params.getString("summon-entity", "");
        int count = params.getInt("count", 1);
        int intervalTicks = params.getInt("interval-ticks", 120);
        int durationTicks = params.getInt("duration-ticks", 600);
        String nextStageId = params.getString("next-stage", null);
        return new SummonStage(id, summonEntity, count, intervalTicks, durationTicks, nextStageId);
    }
}

class SummonStage implements BossStage {

    private final String id;
    private final String summonEntity;
    private final int count;
    private final int intervalTicks;
    private final int durationTicks;
    private final String nextStageId;
    private final List<UUID> summoned = new ArrayList<>();
    private ScheduledTask task;

    SummonStage(String id, String summonEntity, int count, int intervalTicks, int durationTicks, String nextStageId) {
        this.id = id;
        this.summonEntity = summonEntity;
        this.count = count;
        this.intervalTicks = intervalTicks;
        this.durationTicks = durationTicks;
        this.nextStageId = nextStageId;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void onEnter(BossStageContext context) {
        task = context.getStageManager().getScheduler().runTaskTimer(() -> {
            for (UUID uuid : new ArrayList<>(summoned)) {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity == null || entity.isDead()) {
                    summoned.remove(uuid);
                }
            }
            context.getBoss().getBossEntityContext()
                    .ifPresent(ctx -> summoned.addAll(ctx.spawnAdd(summonEntity, count)));
        }, 0, intervalTicks);
    }

    @Override
    public void onExit(BossStageContext context) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID uuid : new ArrayList<>(summoned)) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        summoned.clear();
    }

    @Override
    public boolean shouldTransition(BossStageContext context, long now) {
        if (nextStageId == null || nextStageId.isBlank()) {
            return false;
        }
        return now - context.getStageStartTime() >= durationTicks * 50L;
    }

    @Override
    public String getNextStageId(BossStageContext context) {
        return nextStageId;
    }
}
