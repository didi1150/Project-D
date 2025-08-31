package dev.bukkit.event;

import dev.core.event.CancellableEvent;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import org.bukkit.event.Cancellable;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BukkitEventBus implements EventBusInterface {

    private static final BukkitEventBus instance = new BukkitEventBus();
    public static BukkitEventBus getInstance() {
        return instance;
    }

    private final List<EventAction<?>> subscribed;

    private BukkitEventBus() {
        subscribed = new ArrayList<>();
    }

    public List<EventAction<?>> getSubscribed() {
        return subscribed;
    }

    //can be used in an event-call to execute all the subscribed actions
    public <E> List<EventAction<E>> getSubscribedOfType(Class<E> type) {
        return subscribed.stream()
                .filter(eventAction -> eventAction.getType().equals(type))
                .map(eventAction -> (EventAction<E>) eventAction)
                .sorted()
                .collect(Collectors.toList());
    }

    public EventAction<?> getSubscribedWithId(String id) {
        return subscribed.stream()
                .filter(eventAction -> eventAction.getId().equals(id))
                .findFirst().orElseThrow(() -> new NoSuchElementException("No such EventAction with the given id"));
    }

    public void subscribe(EventAction<?> eventAction) {
        if (subscribed.contains(eventAction)) return;
        subscribed.add(eventAction);
    }

    public void unsubscribe(String id) {
        subscribed.removeIf(eventAction -> eventAction.getId().equals(id));
    }

    public void unsubscribe(EventAction<?> eventAction) {
        subscribed.removeIf(e -> e.equals(eventAction));
    }

    public <E> void sendEvent(E event) {
        List<EventAction<E>> eventActions = getSubscribedOfType((Class<E>) event.getClass());
        for (EventAction<E> eventAction : eventActions) {
            if (event instanceof CancellableEvent cancellableEvent) {
                if (cancellableEvent.isCancelled()) break;
            }
            if (event instanceof Cancellable cancellable) {
                if (cancellable.isCancelled()) break;
            }
            eventAction.execute(event);
        }
    }

    public <E> void sendEvent(E event, Function<E, Boolean> condition) {
        List<EventAction<E>> eventActions = getSubscribedOfType((Class<E>) event.getClass());
        for (EventAction<E> eventAction : eventActions) {
            if (event instanceof CancellableEvent cancellableEvent) {
                if (cancellableEvent.isCancelled()) break;
            }
            if (event instanceof Cancellable cancellable) {
                if (cancellable.isCancelled()) break;
            }
            if (condition.apply(event)) break;
            eventAction.execute(event);
        }
    }

}
