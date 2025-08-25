package dev.core.event;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class EventAction<E> {

    private final Consumer<E> action;
    private final String id;
    private final Class<E> type;

    /// Example:
    ///
    /// {@code EventAction<BlockBreakEvent> blockBreakEventAction = new EventAction<>(event -> {
    /// event.foo() //do something with the event
    /// }, BlockBreakEvent.class);}
    public EventAction(Consumer<E> action, Class<E> type) {
        this.action = action;
        this.id = UUID.randomUUID().toString();
        this.type = type;
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        EventAction<?> that = (EventAction<?>) o;
        return Objects.equals(id, that.id);
    }

}
