package dev.core.event;

import dev.bukkit.event.BukkitEventBus;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

public class EventBusTest {

    private static EventBusInterface eventBus;
    private static EventAction<PlayerJoinEvent> playerJoinEventAction;

    private static int countInteger;

    @BeforeAll
    static void setup(){
        eventBus = BukkitEventBus.getInstance();
        playerJoinEventAction = new EventAction<>(e -> {countInteger--;}, PlayerJoinEvent.class);
        eventBus.subscribe(playerJoinEventAction);
        EventAction<BlockBreakEvent> blockBreakEventAction = new EventAction<>(e -> {countInteger += 2;}, BlockBreakEvent.class);
        eventBus.subscribe(blockBreakEventAction);
        eventBus.subscribe(new EventAction<>(e -> countInteger += 10, Event.class));
        eventBus.subscribe(new EventAction<>(e -> countInteger += 100, CancellableEvent.class, 3));
        eventBus.subscribe(new EventAction<>(e -> countInteger *= 0, CancellableEvent.class, 5));
        eventBus.subscribe(new EventAction<>(e -> e.setCancelled(true), CancellableEvent.class, 4));
    }

    @BeforeEach
    void start() {
        countInteger = 0;
    }

    @Test
    void testSubscribe() {
        EventAction<?> eventAction = new EventAction<>(e -> {}, null);
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
        assertEquals(size-1, eventBus.getSubscribed().size());
    }

    @Test
    void testGetSubscribedOfType() {
        List<EventAction<PlayerJoinEvent>> list = eventBus.getSubscribedOfType(PlayerJoinEvent.class);
        assertEquals(1, list.size());
        assertEquals(playerJoinEventAction.getId(), list.getFirst().getId());
    }

//    @Test
//    void testConsumerGetsCalled() {
//        for (EventAction<?> eventAction : eventBus.getSubscribed()) {
//            eventAction.execute(null);
//            assertTrue(countInteger != 0);
//        }
//        assertEquals(1, countInteger);
//    }

    @Test
    void test() {
        List<Event> events = new ArrayList<>(List.of(new Event[]{new CancellableEvent(), new CancellableEvent()}));
        for (Event event : events) {
            eventBus.sendEvent(event);
        }
        assertEquals(0, countInteger);


    }

}
