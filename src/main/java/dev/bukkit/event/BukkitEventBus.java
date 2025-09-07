package dev.bukkit.event;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bukkit.event.Cancellable;

import dev.core.event.CoreCancellable;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;

public class BukkitEventBus implements EventBusInterface {

    private static BukkitEventBus instance;

    public static BukkitEventBus getInstance() {
        if (instance == null) {
            instance = new BukkitEventBus();
        }
        return instance;
    }

    private final List<EventAction<?>> subscribed;

    private BukkitEventBus() {
        subscribed = new ArrayList<>();
    }

    public List<EventAction<?>> getSubscribed() {
        return subscribed;
    }

    // can be used in an event-call to execute all the subscribed actions
    @SuppressWarnings("unchecked")
    public <E> List<EventAction<E>> getSubscribedOfType(Class<E> type) {
        return subscribed.stream().filter(eventAction -> eventAction.getType().equals(type))
                .map(eventAction -> (EventAction<E>) eventAction).sorted().collect(Collectors.toList());
    }

    public EventAction<?> getSubscribedWithId(String id) {
        return subscribed.stream().filter(eventAction -> eventAction.getId().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("No such EventAction with the given id"));
    }

    public void subscribe(EventAction<?> eventAction) {
        if (subscribed.contains(eventAction))
            return;
        subscribed.add(eventAction);
    }

    @Override
    public <E> void subscribeOnce(EventAction<E> eventAction) {
        @SuppressWarnings("unchecked")
        final EventAction<E>[] holder = new EventAction[1];

        holder[0] = new EventAction<>(event -> {
            eventAction.execute(event);
            unsubscribe(holder[0]);
        }, eventAction.getType(), eventAction.getPriority());

        subscribe(holder[0]);
    }

    public void unsubscribe(String id) {
        subscribed.removeIf(eventAction -> eventAction.getId().equals(id));
    }

    public void unsubscribe(EventAction<?> eventAction) {
        subscribed.removeIf(e -> e.equals(eventAction));
    }

    public <E> void sendEvent(E event) {
        @SuppressWarnings("unchecked")
        List<EventAction<E>> eventActions = getSubscribedOfType((Class<E>) event.getClass());
        for (EventAction<E> eventAction : eventActions) {
            if (event instanceof CoreCancellable coreCancellable) {
                if (coreCancellable.isCancelled()) {
                    break;
                }
            }
            if (event instanceof Cancellable cancellable) {
                if (cancellable.isCancelled())
                    break;
            }
            eventAction.execute(event);
        }
    }

    public <E> void sendEvent(E event, Function<E, Boolean> condition) {
        @SuppressWarnings("unchecked")
        List<EventAction<E>> eventActions = getSubscribedOfType((Class<E>) event.getClass());
        for (EventAction<E> eventAction : eventActions) {
            if (event instanceof CoreCancellable coreCancellable) {
                if (coreCancellable.isCancelled()) {
                    break;
                }
            }
            if (event instanceof Cancellable cancellable) {
                if (cancellable.isCancelled())
                    break;
            }
            if (condition.apply(event))
                break;
            eventAction.execute(event);
        }
    }

}
