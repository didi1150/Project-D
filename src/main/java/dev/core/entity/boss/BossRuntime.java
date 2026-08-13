package dev.core.entity.boss;

import java.util.ArrayList;
import java.util.List;

import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;

public abstract class BossRuntime {

    private final EventBusInterface eventBus;
    private final List<EventAction<?>> registeredTriggers = new ArrayList<>();

    protected BossRuntime(EventBusInterface eventBus) {
        this.eventBus = eventBus;
    }

    public final void register() {
        registeredTriggers.clear();
        registerBossTriggers();
    }

    public final void unregister() {
        for (EventAction<?> action : new ArrayList<>(registeredTriggers)) {
            eventBus.unsubscribe(action);
        }
        registeredTriggers.clear();
    }

    protected abstract void registerBossTriggers();

    protected final <E> void registerTrigger(java.util.function.Consumer<E> handler, Class<E> type) {
        EventAction<E> action = new EventAction<>(handler, type);
        eventBus.subscribe(action);
        registeredTriggers.add(action);
    }

    protected final <E> void registerTrigger(java.util.function.Consumer<E> handler, Class<E> type, int priority) {
        EventAction<E> action = new EventAction<>(handler, type, priority);
        eventBus.subscribe(action);
        registeredTriggers.add(action);
    }

    public EventBusInterface getEventBus() {
        return eventBus;
    }
}
