package dev.core.event;

import java.util.function.Consumer;

public abstract class EventActionAbstract<E> extends EventAction<E> {

    public EventActionAbstract(Class<E> type, int priority) {
        super(null, type, priority);
    }

    public EventActionAbstract(Class<E> type) {
        super(null, type);
    }

    @Override
    public Consumer<E> getAction() {
        return this::onAction;
    }

    public abstract void onAction(E e);

    @Override
    public void execute(E event) {
        onAction(event);
    }
}
