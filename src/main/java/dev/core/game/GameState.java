package dev.core.game;

import java.util.ArrayList;
import java.util.List;

import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.event.EventSubscriptionManager;

public abstract class GameState {
    protected final String name;
    protected final long duration; // -1 for infinite duration (in ticks)
    protected GameStateListener listener;
    protected TaskScheduler scheduler;
    protected ScheduledTask durationTask;
    protected boolean active = false;
    protected long startTime;
    protected long remainingTicks;
    private EventBusInterface eventBus;
    protected EventSubscriptionManager subscriptionManager;

    public GameState(String name, long duration, EventBusInterface eventBus) {
        this.name = name;
        this.duration = duration;
        this.remainingTicks = duration;
        this.eventBus = eventBus;
        this.subscriptionManager = new EventSubscriptionManager(eventBus);
    }

    // Called when state becomes active
    public final void start(GameStateListener listener, TaskScheduler scheduler) {
        this.listener = listener;
        this.scheduler = scheduler;
        this.active = true;
        this.startTime = System.currentTimeMillis();
        this.remainingTicks = duration;

        if (listener != null) {
            listener.onStateStart(this);
        }

        onStart();
        registerSubscribers();

        // Schedule tick-based countdown if not infinite
        if (duration > 0 && scheduler != null) {
            this.durationTask = scheduler.runTaskTimer(() -> {
                if (!active)
                    return;

                remainingTicks--;

                // Call onTick every 20 ticks (1 second)
                if (remainingTicks % 20 == 0) {
                    onTickSecond(remainingTicks / 20); // Pass seconds remaining
                }

                // Complete when time runs out
                if (remainingTicks <= 0) {
                    complete(GameStateResult.COMPLETE);
                }
            }, 0L, 1L); // Run every tick
        }
    }

    // Called when state should stop
    public final void stop() {
        if (!active) {
            return;
        }

        active = false;

        if (durationTask != null && !durationTask.isCancelled()) {
            durationTask.cancel();
            durationTask = null;
        }

        onStop();
        unregisterSubscribers();

        if (listener != null) {
            listener.onStateEnd(this);
        }
    }

    // States signal completion
    protected final void complete(GameStateResult result) {
        if (!active) {
            return;
        }

        if (listener != null) {
            if (result == GameStateResult.SKIP) {
                listener.onStateSkip(this);
            } else {
                listener.onStateComplete(this, result);
            }
        }
    }

    protected final void jumpToState(String name) {
        if (!active) {
            return;
        }

        if (listener != null) {
            listener.onStateJump(this, name);
        }
    }

    protected abstract void onStart();

    protected abstract void onStop();

    protected abstract void registerSubscribers();

    /**
     * Helper method to add event subscribers using the subscription manager.
     * Automatically tracks subscriptions for cleanup.
     */
    protected void addSubscriber(EventAction<?> eventAction) {
        subscriptionManager.subscribe(eventAction);
    }

    private void unregisterSubscribers() {
        subscriptionManager.unsubscribeAll();
    }

    /**
     * Called every second (20 ticks) when this state has a duration.
     * 
     * @param secondsRemaining The number of seconds remaining in this state
     */
    protected void onTickSecond(long secondsRemaining) {
    }

    // Getters
    public String getName() {
        return name;
    }

    public long getDuration() {
        return duration;
    }

    public long getDurationInSeconds() {
        return duration > 0 ? duration / 20 : -1;
    }

    public boolean isActive() {
        return active;
    }

    public long getTimeRemaining() {
        return Math.max(0, remainingTicks);
    }

    public long getTimeRemainingInSeconds() {
        return Math.max(0, remainingTicks / 20);
    }

    public TaskScheduler getScheduler() {
        return scheduler;
    }

    // Optional: Can be overridden for custom skip logic
    public boolean canSkip() {
        return true;
    }
}