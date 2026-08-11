package dev.core.entity.boss;

import java.util.List;
import java.util.Optional;

import dev.core.game.ScheduledTask;

/**
 * Final stage of a boss fight: the boss appears dead (0 health) but keeps
 * broadcasting monologue lines before being removed. When the monologue is
 * finished the boss completes its defeat (death event + removal).
 */
public class MonologueBossStage implements BossStage {

    private final String id;
    private final List<String> lines;
    private final int lineIntervalTicks;
    private ScheduledTask task;

    public MonologueBossStage(String id, List<String> lines, int lineIntervalTicks) {
        this.id = id;
        this.lines = lines == null ? List.of() : List.copyOf(lines);
        this.lineIntervalTicks = lineIntervalTicks;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void onEnter(BossStageContext context) {
        RPGBossEntity boss = context.getBoss();
        boss.setMovementStrategy(new NoopMovementStrategy());
        boss.setTargetingStrategy(mob -> Optional.empty());
        boss.setAttackPattern((mob, target, now) -> {
        });

        if (lines.isEmpty()) {
            boss.completeDefeat();
            return;
        }

        int[] index = { 0 };
        task = context.getStageManager().getScheduler().runTaskTimer(() -> {
            int current = index[0]++;
            if (current >= lines.size()) {
                if (task != null) {
                    task.cancel();
                }
                boss.completeDefeat();
                return;
            }
            boss.getBossEntityContext().ifPresent(ctx -> ctx.broadcast(lines.get(current)));
        }, 0, lineIntervalTicks);
    }

    @Override
    public void onExit(BossStageContext context) {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
