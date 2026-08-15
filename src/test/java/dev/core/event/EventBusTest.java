package dev.core.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.bukkit.event.BukkitEventBus;
import net.kyori.adventure.text.Component;

public class EventBusTest {

	private static EventBusInterface eventBus;
	private static EventAction<PlayerJoinEvent> playerJoinEventAction;

	private static int countInteger;

	@BeforeAll
	static void setup() {
		eventBus = BukkitEventBus.getInstance();
		playerJoinEventAction = new EventAction<>(e -> {
			countInteger--;
		}, PlayerJoinEvent.class);
		eventBus.subscribe(playerJoinEventAction);
		EventAction<BlockBreakEvent> blockBreakEventAction = new EventAction<>(e -> {
			countInteger += 2;
		}, BlockBreakEvent.class);
		eventBus.subscribe(blockBreakEventAction);
		eventBus.subscribe(new EventAction<>(e -> countInteger += 10, Event.class));
		eventBus.subscribe(new EventAction<>(e -> countInteger += 100, TestCancellableEvent.class, 3));
		eventBus.subscribe(new EventAction<>(e -> countInteger *= 0, TestCancellableEvent.class, 5));
		eventBus.subscribe(new EventAction<>(e -> e.setCancelled(true), TestCancellableEvent.class, 4));
	}

	@BeforeEach
	void start() {
		countInteger = 0;
	}

	@Test
	void testSubscribe() {
		EventAction<?> eventAction = new EventAction<>(e -> {
		}, null);
		assertFalse(eventBus.getSubscribed().contains(eventAction));
		assertThrows(NoSuchElementException.class, () -> eventBus.getSubscribedWithId(eventAction.getId()));
		eventBus.subscribe(eventAction);
		assertTrue(eventBus.getSubscribed().contains(eventAction));
		assertDoesNotThrow(() -> eventBus.getSubscribedWithId(eventAction.getId()));
		assertEquals(eventAction, eventBus.getSubscribedWithId(eventAction.getId()));
	}

	@Test
	void testUnsubscribe() {
		assertTrue(eventBus.getSubscribed().contains(playerJoinEventAction));
		assertDoesNotThrow(() -> eventBus.getSubscribedWithId(playerJoinEventAction.getId()));
		assertEquals(playerJoinEventAction, eventBus.getSubscribedWithId(playerJoinEventAction.getId()));
		int size = eventBus.getSubscribed().size();
		eventBus.unsubscribe(playerJoinEventAction.getId());
		assertFalse(eventBus.getSubscribed().contains(playerJoinEventAction));
		assertThrows(NoSuchElementException.class, () -> eventBus.getSubscribedWithId(playerJoinEventAction.getId()));
		assertEquals(size - 1, eventBus.getSubscribed().size());
	}

	@Test
	void testGetSubscribedOfType() {
		List<EventAction<PlayerJoinEvent>> list = eventBus.getSubscribedOfType(PlayerJoinEvent.class);
		assertEquals(1, list.size());
		assertEquals(playerJoinEventAction.getId(), list.getFirst().getId());
	}

	@Test
	void testConsumerGetsCalled() {
		// Reset counter
		countInteger = 0;
		// Send a PlayerJoinEvent → subscribed action decrements once
		eventBus.sendEvent(new PlayerJoinEvent(null, "join"));
		assertEquals(-1, countInteger);

		// Send a BlockBreakEvent → subscribed action adds 2
		eventBus.sendEvent(new BlockBreakEvent(null, null));
		assertEquals(1, countInteger); // -1 + 2 = 1

		// Send a cancellable event
		TestCancellableEvent cancellableEvent = new TestCancellableEvent();
		eventBus.sendEvent(cancellableEvent);

		// Order of subscribers for CancellableEvent (by priority):
		// *0 (priority 5)
		// setCancelled(true) (priority 4) → cancels
		// *0 (priority 3) should NOT run because event was cancelled before
		//
		// So we expect: 1 (current) * 0 = 0
		assertEquals(0, countInteger);
	}

	@Test
	void test() {
		List<Event> events = new ArrayList<>(
				List.of(new Event[] { new TestCancellableEvent(), new TestCancellableEvent() }));
		for (Event event : events) {
			eventBus.sendEvent(event);
		}
		assertEquals(0, countInteger);

	}

}
