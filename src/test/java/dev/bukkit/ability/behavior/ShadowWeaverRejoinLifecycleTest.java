package dev.bukkit.ability.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.ActiveAbility;
import dev.core.event.EventAction;
import dev.core.event.EventSubscriptionManager;

/**
 * Regression tests for disconnect + rejoin: the staff binds TWO abilities
 * (PLACE + DASH), each constructing a behavior that bumps the shared
 * per-holder refcount. The quit path must fully tear that generation down
 * (state, tick task, refcount, cache, bus subscriptions) so the next
 * equip session re-arms its subscriptions and render loop instead of
 * silently staying dead (the old bug: stale refcount made every future
 * {@code onActivate} skip its isFirst gate, permanently disabling teleport
 * and the indicator/particle task).
 */
class ShadowWeaverRejoinLifecycleTest {

    private UUID uuid;
    private Player player;
    private PlayerQuitEvent quitEvent;

    @BeforeEach
    void setUp() {
        uuid = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);

        quitEvent = mock(PlayerQuitEvent.class);
        when(quitEvent.getPlayer()).thenReturn(player);

        ShadowWeaverBehavior.resetForTest();
    }

    @AfterEach
    void tearDown() {
        ShadowWeaverBehavior.resetForTest();
    }

    /** Mocks one holder generation; a rejoin produces a fresh holder with the SAME uuid. */
    private BukkitPlayerEntity newHolder() {
        BukkitPlayerEntity holder = mock(BukkitPlayerEntity.class);
        when(holder.getUuid()).thenReturn(uuid);
        when(holder.getPlayer()).thenReturn(Optional.of(player));
        return holder;
    }

    private ActiveAbility newCtx(BukkitPlayerEntity holder, EventSubscriptionManager subs) {
        ActiveAbility ctx = mock(ActiveAbility.class);
        when(ctx.getHolder()).thenReturn(holder);
        when(ctx.getSubscriptions()).thenReturn(subs);
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private List<EventAction<?>> captureSubscriptions(EventSubscriptionManager subs) {
        List<EventAction<?>> captured = new ArrayList<>();
        doAnswer(inv -> {
            captured.add((EventAction<?>) inv.getArgument(0));
            return null;
        }).when(subs).subscribe(any());
        return captured;
    }

    @SuppressWarnings("unchecked")
    private EventAction<PlayerQuitEvent> quitActionOf(List<EventAction<?>> subscribed) {
        return subscribed.stream()
                .filter(a -> a.getType() == PlayerQuitEvent.class)
                .map(a -> (EventAction<PlayerQuitEvent>) a)
                .findFirst()
                .orElseThrow(() -> new AssertionError("onQuit was not subscribed"));
    }

    @Test
    void twoAbilityEquipRefcountsTwiceAndOnlyFirstActivationSubscribes() {
        EventSubscriptionManager subs = mock(EventSubscriptionManager.class);
        List<EventAction<?>> subscribed = captureSubscriptions(subs);

        // Mirrors EquipmentManager.bindAbility order: PLACE is constructed AND
        // activated before DASH is even constructed.
        ActiveAbility placeCtx = newCtx(newHolder(), subs);
        ShadowWeaverBehavior first = new ShadowWeaverBehavior(placeCtx);
        assertEquals(1, ShadowWeaverBehavior.refCountForTest(uuid));
        first.onActivate(placeCtx);
        verify(subs, times(4)).subscribe(any()); // move / sneak / quit / death
        assertTrue(ShadowWeaverBehavior.hasStateForTest(uuid));

        ActiveAbility dashCtx = newCtx(newHolder(), subs);
        ShadowWeaverBehavior second = new ShadowWeaverBehavior(dashCtx);
        assertEquals(2, ShadowWeaverBehavior.refCountForTest(uuid));

        // Second behavior (DASH) must not double-subscribe.
        second.onActivate(dashCtx);
        verify(subs, times(4)).subscribe(any());

        // Headless: no scheduler available, so the task handle is never stored.
        assertFalse(ShadowWeaverBehavior.hasTickTaskForTest(uuid));
        assertEquals(4, subscribed.size());
    }

    @Test
    void quitFullyTearsDownTheSession() {
        EventSubscriptionManager subs = mock(EventSubscriptionManager.class);
        List<EventAction<?>> subscribed = captureSubscriptions(subs);
        ActiveAbility ctx = newCtx(newHolder(), subs);
        ShadowWeaverBehavior first = new ShadowWeaverBehavior(ctx);
        first.onActivate(ctx);

        quitActionOf(subscribed).execute(quitEvent);

        assertEquals(0, ShadowWeaverBehavior.refCountForTest(uuid));
        assertFalse(ShadowWeaverBehavior.hasStateForTest(uuid));
        assertFalse(ShadowWeaverBehavior.hasTickTaskForTest(uuid));
        // Defuses zombie listeners: without this the old generation's quit
        // handler fires again on the player's NEXT quit and wipes the live
        // generation's state.
        verify(subs).unsubscribeAll();
    }

    @Test
    void rejoinRearmsSubscriptionsAndRefcount() {
        // Generation 1: equip then disconnect.
        EventSubscriptionManager gen1Subs = mock(EventSubscriptionManager.class);
        List<EventAction<?>> gen1Subscribed = captureSubscriptions(gen1Subs);
        ActiveAbility gen1Ctx = newCtx(newHolder(), gen1Subs);
        ShadowWeaverBehavior gen1 = new ShadowWeaverBehavior(gen1Ctx);
        gen1.onActivate(gen1Ctx);
        quitActionOf(gen1Subscribed).execute(quitEvent);

        // Generation 2 (rejoined player): fresh entity + fresh subscription
        // manager, same uuid; PLACE binds + activates before DASH.
        EventSubscriptionManager gen2Subs = mock(EventSubscriptionManager.class);
        List<EventAction<?>> gen2Subscribed = captureSubscriptions(gen2Subs);
        ActiveAbility gen2PlaceCtx = newCtx(newHolder(), gen2Subs);
        ShadowWeaverBehavior gen2First = new ShadowWeaverBehavior(gen2PlaceCtx);
        // Pre-fix this was 3: the stale count survived logout and merged upward,
        // so neither new behavior passed the isFirst gate ever again.
        assertEquals(1, ShadowWeaverBehavior.refCountForTest(uuid));

        gen2First.onActivate(gen2PlaceCtx);
        // THE regression assertion: pre-fix code subscribed nothing here, so
        // no move/sneak/quit/death handlers and no render loop ever came back.
        verify(gen2Subs, times(4)).subscribe(any());
        assertNotNull(quitActionOf(gen2Subscribed), "quit handler re-armed");
        assertTrue(ShadowWeaverBehavior.hasStateForTest(uuid));

        // The DASH behavior of generation 2 must not double-subscribe.
        ActiveAbility gen2DashCtx = newCtx(newHolder(), gen2Subs);
        ShadowWeaverBehavior gen2Second = new ShadowWeaverBehavior(gen2DashCtx);
        assertEquals(2, ShadowWeaverBehavior.refCountForTest(uuid));
        gen2Second.onActivate(gen2DashCtx);
        verify(gen2Subs, times(4)).subscribe(any());
    }

    @Test
    void doubleQuitIsIdempotent() {
        EventSubscriptionManager subs = mock(EventSubscriptionManager.class);
        List<EventAction<?>> subscribed = captureSubscriptions(subs);
        ActiveAbility ctx = newCtx(newHolder(), subs);
        ShadowWeaverBehavior first = new ShadowWeaverBehavior(ctx);
        first.onActivate(ctx);

        EventAction<PlayerQuitEvent> quit = quitActionOf(subscribed);
        quit.execute(quitEvent);
        quit.execute(quitEvent); // duplicate dispatch (e.g. death+quit) is a no-op

        assertEquals(0, ShadowWeaverBehavior.refCountForTest(uuid));
        assertFalse(ShadowWeaverBehavior.hasStateForTest(uuid));
        // Every dispatch releases the listeners again; harmless no-op repeats.
        verify(subs, atLeastOnce()).unsubscribeAll();
    }
}
