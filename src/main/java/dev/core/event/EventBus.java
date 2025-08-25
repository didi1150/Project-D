package dev.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

public class EventBus {

    private static final EventBus instance = new EventBus();
    public static EventBus getInstance() {
        return instance;
    }

    private final List<EventAction<?>> subscribed;

    private EventBus() {
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

}
