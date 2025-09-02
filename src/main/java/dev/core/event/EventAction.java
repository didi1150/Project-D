package dev.core.event;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class EventAction<E> implements Comparable<EventAction<E>> {

    private final Consumer<E> action;
    private final String id;
    private final Class<E> type;
    private final int priority;

    public static final int LOWEST_PRIORITY = 1;
    public static final int LOW_PRIORITY = 2;
    public static final int NORMAL_PRIORITY = 3;
    public static final int HIGH_PRIORITY = 4;
    public static final int HIGHEST_PRIORITY = 5;

    /// @param action action that is executed when the event is called
    /// @param type class of the event that is listened to
    /// @param priority priority of the eventAction (ranges from 1 to 5)
    /// @implNote
    /// {@code EventAction<BlockBreakEvent> blockBreakEventAction = new EventAction<>(event -> {
    /// event.foo() //do something with the event
    /// }, BlockBreakEvent.class, 1);}
    ///
    public EventAction(Consumer<E> action, Class<E> type, int priority) {
        this.action = action;
        this.id = UUID.randomUUID().toString();
        this.type = type;
        if (priority < 0 || priority > 5) throw new IllegalArgumentException("The priority of an EventAction must be between 1-5!");
        this.priority = priority;
    }

    public EventAction(Consumer<E> action, Class<E> type) {
        this(action, type, NORMAL_PRIORITY);
    }

    public Consumer<E> getAction() {
        return action;
    }

    public String getId() {
        return id;
    }

    public Class<E> getType() {
        return type;
    }

    public void execute(E event) {
        action.accept(event);
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        EventAction<?> that = (EventAction<?>) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int compareTo(EventAction<E> eventAction) {
        return eventAction.getPriority() - this.getPriority();
    }
}
