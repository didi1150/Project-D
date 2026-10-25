package dev.core.entity.boss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import dev.core.game.TaskScheduler;

class BossStageManagerStateTest {

    @Test
    void stateIsScopedToTheStageAndTheEncounter() {
        RPGBossEntity firstBoss = mock(RPGBossEntity.class);
        when(firstBoss.isAlive()).thenReturn(true);
        TaskScheduler scheduler = mock(TaskScheduler.class);

        BossStageManager first = new BossStageManager(firstBoss, scheduler);
        first.addStage(new NoopStage("grow"));
        first.setInitialStage("grow");

        Object state = new Object();
        first.setState(state);

        assertSame(state, first.getState());
        assertTrue(first.hasStage("grow"));
        assertFalse(first.hasStage("missing"));

        RPGBossEntity secondBoss = mock(RPGBossEntity.class);
        when(secondBoss.isAlive()).thenReturn(true);
        BossStageManager second = new BossStageManager(secondBoss, scheduler);
        second.addStage(new NoopStage("grow"));
        second.setInitialStage("grow");

        assertNull(second.getState());
    }

    @Test
    void stopExitsTheCurrentStageOnceAndIsNullSafe() {
        RPGBossEntity boss = mock(RPGBossEntity.class);
        when(boss.isAlive()).thenReturn(true);
        BossStageManager manager = new BossStageManager(boss, mock(TaskScheduler.class));
        NoopStage stage = new NoopStage("grow");
        manager.addStage(stage);
        manager.setInitialStage("grow");

        manager.stop();
        manager.stop();

        assertTrue(stage.exited);
    }

    private static final class NoopStage implements BossStage {

        private final String id;
        private boolean exited;

        private NoopStage(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public void onExit(BossStageContext context) {
            exited = true;
        }
    }
}
