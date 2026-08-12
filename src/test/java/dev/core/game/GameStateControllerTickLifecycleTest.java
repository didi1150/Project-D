package dev.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.core.ability.Effect;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityManager;
import dev.core.event.EventBusInterface;
import dev.core.event.impl.TickEvent;

/**
 * The effect/entity tick must be owned by the GameStateController and be active
 * in every state: started when the machine starts (first state), stopped when it
 * stops or when the last state completes.
 */
class GameStateControllerTickLifecycleTest {

    @Test
    void tickStartsWithFirstStateAndStopsOnFullCompletion() {
        RecordingScheduler scheduler = new RecordingScheduler();
        CountingEffectManager effectManager = new CountingEffectManager();
        TestState state = new TestState();

        GameStateController controller = new GameStateController(scheduler, effectManager, EntityManager.getInstance());
        controller.addState(state);

        controller.start();
        assertNotNull(scheduler.timerTask, "controller must start a global tick task");
        assertEquals(0L, scheduler.delayTicks, "tick must start immediately (first state)");
        assertEquals(1L, scheduler.periodTicks, "tick must run every server tick");
        assertEquals(1, scheduler.runTaskTimerCalls, "state with infinite duration must not add its own tick");

        // drive the controller's tick -> effectManager must be ticked
        scheduler.runTimer();
        assertEquals(1, effectManager.ticks);

        // finishing the (single) last state stops the tick
        state.finish();
        assertTrue(scheduler.lastTaskCancelled, "tick must stop when the last state completes");
    }

    @Test
    void tickStopsOnControllerShutdown() {
        RecordingScheduler scheduler = new RecordingScheduler();
        CountingEffectManager effectManager = new CountingEffectManager();

        GameStateController controller = new GameStateController(scheduler, effectManager, EntityManager.getInstance());
        controller.addState(new TestState());
        controller.start();
        assertEquals(1, scheduler.runTaskTimerCalls);

        controller.stop();
        assertTrue(scheduler.lastTaskCancelled, "tick must stop when the controller stops");
    }

    // ---------------------------------------------------------------- stubs

    private static final class TestState extends GameState {
        TestState() {
            super("TEST_STATE", -1, null);
        }

        @Override
        protected void onStart() {
        }

        @Override
        protected void onStop() {
        }

        @Override
        protected void registerSubscribers() {
        }

        void finish() {
            complete(GameStateResult.COMPLETE);
        }
    }

    private static final class CountingEffectManager implements EffectManagerInterface {
        int ticks = 0;

        @Override
        public void tick(long now) {
            ticks++;
        }

        @Override
        public Effect cast(dev.core.entity.RPGEntity entity, dev.core.ability.Ability ability) {
            return null;
        }

        @Override
        public boolean canActivate(dev.core.entity.RPGEntity entity, dev.core.ability.Ability ability) {
            return false;
        }

        @Override
        public long remainingCooldown(dev.core.entity.RPGEntity entity, dev.core.ability.Ability ability) {
            return 0;
        }

        @Override
        public void cancelAll() {
        }
    }

    private static final class RecordingScheduler implements TaskScheduler {
        Runnable timerTask;
        long delayTicks;
        long periodTicks;
        int runTaskTimerCalls = 0;
        boolean lastTaskCancelled = false;

        @Override
        public ScheduledTask runTaskLater(Runnable task, long delayTicks) {
            return noopTask();
        }

        @Override
        public ScheduledTask runTaskLaterAsync(Runnable task, long delayTicks) {
            return noopTask();
        }

        @Override
        public ScheduledTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
            runTaskTimerCalls++;
            this.timerTask = task;
            this.delayTicks = delayTicks;
            this.periodTicks = periodTicks;
            lastTaskCancelled = false;
            return new ScheduledTask() {
                @Override
                public void cancel() {
                    lastTaskCancelled = true;
                }

                @Override
                public boolean isCancelled() {
                    return lastTaskCancelled;
                }
            };
        }

        @Override
        public void cancelAllTasks() {
        }

        void runTimer() {
            if (timerTask != null) {
                timerTask.run();
            }
        }

        private static ScheduledTask noopTask() {
            return new ScheduledTask() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }
}