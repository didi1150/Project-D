package dev.core.event;

import java.util.List;
import java.util.function.Function;

public interface EventBusInterface {

	public List<EventAction<?>> getSubscribed();

	public <E> List<EventAction<E>> getSubscribedOfType(Class<E> type);

	public EventAction<?> getSubscribedWithId(String id);

	public void subscribe(EventAction<?> eventAction);

	public <E> void subscribeOnce(EventAction<E> eventAction);

	public void unsubscribe(String id);

	public void unsubscribe(EventAction<?> eventAction);

	public <E> void sendEvent(E event);

	public <E> void sendEvent(E event, Function<E, Boolean> condition);

}
