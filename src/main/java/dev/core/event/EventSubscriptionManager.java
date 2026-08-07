package dev.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

/**
 * Utility for managing event subscriptions with automatic cleanup.
 * Reduces boilerplate in GameState and other event-heavy classes.
 */
public class EventSubscriptionManager {

    private final EventBusInterface eventBus;
    private final List<String> subscriptionIds = new ArrayList<>();

    public EventSubscriptionManager(@NotNull EventBusInterface eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Subscribe to an event and track it for automatic cleanup.
     */
    public <E> void subscribe(@NotNull EventAction<E> eventAction) {
        eventBus.subscribe(eventAction);
        subscriptionIds.add(eventAction.getId());
    }

    /**
     * Subscribe to an event that only fires once.
     */
    public <E> void subscribeOnce(@NotNull EventAction<E> eventAction) {
        eventBus.subscribeOnce(eventAction);
        subscriptionIds.add(eventAction.getId());
    }

    /**
     * Subscribe to an event with a condition.
     */
    public <E> void subscribeOnCondition(@NotNull EventAction<E> eventAction, java.util.function.Predicate<E> predicate) {
        eventBus.subscribeOnCondition(eventAction, predicate);
        subscriptionIds.add(eventAction.getId());
    }

    /**
     * Unsubscribe a specific subscription by ID.
     */
    public void unsubscribe(@NotNull String id) {
        eventBus.unsubscribe(id);
        subscriptionIds.remove(id);
    }

    /**
     * Unsubscribe a specific subscription by EventAction.
     */
    public void unsubscribe(@NotNull EventAction<?> eventAction) {
        eventBus.unsubscribe(eventAction);
        subscriptionIds.remove(eventAction.getId());
    }

    /**
     * Unsubscribe all tracked subscriptions.
     */
    public void unsubscribeAll() {
        for (String id : new ArrayList<>(subscriptionIds)) {
            eventBus.unsubscribe(id);
        }
        subscriptionIds.clear();
    }

    /**
     * Get count of active subscriptions.
     */
    public int getActiveSubscriptionCount() {
        return subscriptionIds.size();
    }

    /**
     * Create a simple event action with a handler.
     */
    public static <E> EventAction<E> createAction(@NotNull java.util.function.Consumer<E> handler, @NotNull Class<E> eventType) {
        return new EventAction<>(handler, eventType);
    }

    /**
     * Create a simple event action with a handler and priority.
     */
    public static <E> EventAction<E> createAction(@NotNull java.util.function.Consumer<E> handler, @NotNull Class<E> eventType, int priority) {
        return new EventAction<>(handler, eventType, priority);
    }
}
