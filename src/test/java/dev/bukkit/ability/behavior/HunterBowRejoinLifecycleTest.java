package dev.bukkit.ability.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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
 * Regression tests for disconnect + rejoin on the hunter's bow: the quit path
 * must drop the per-holder refcount and release the generation's listeners so
 * the next equip session re-arms its shoot/hit handlers (stamped bounce and
 * explosive arrows) instead of staying dead behind a stale refcount.
 */
class HunterBowRejoinLifecycleTest {

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

        HunterBowBehavior.resetForTest();
    }

    @AfterEach
    void tearDown() {
        HunterBowBehavior.resetForTest();
    }

    private BukkitPlayerEntity newHolder() {
        BukkitPlayerEntity holder = mock(BukkitPlayerEntity.class);
        when(holder.getUuid()).thenReturn(uuid);
        when(holder.getPlayer()).thenReturn(Optional.of(player));
        return holder;
    }

    private ActiveAbility newCtx(EventSubscriptionManager subs) {
        BukkitPlayerEntity holder = newHolder(); // build BEFORE stubbing ctx
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
    void quitDropsRefcountAndUnsubscribes() {
        EventSubscriptionManager subs = mock(EventSubscriptionManager.class);
        List<EventAction<?>> subscribed = captureSubscriptions(subs);
        ActiveAbility ctx = newCtx(subs);
        HunterBowBehavior first = new HunterBowBehavior(ctx);
        assertEquals(1, HunterBowBehavior.refCountForTest(uuid));
        first.onActivate(ctx);
        verify(subs, times(3)).subscribe(any()); // shoot / hit / quit

        quitActionOf(subscribed).execute(quitEvent);

        assertEquals(0, HunterBowBehavior.refCountForTest(uuid));
        verify(subs).unsubscribeAll();
    }

    @Test
    void rejoinRearmsShootAndHitHandlers() {
        // Generation 1: equip then disconnect.
        EventSubscriptionManager gen1Subs = mock(EventSubscriptionManager.class);
        List<EventAction<?>> gen1Subscribed = captureSubscriptions(gen1Subs);
        ActiveAbility gen1Ctx = newCtx(gen1Subs);
        HunterBowBehavior gen1 = new HunterBowBehavior(gen1Ctx);
        gen1.onActivate(gen1Ctx);
        quitActionOf(gen1Subscribed).execute(quitEvent);

        // Generation 2 (rejoined player): fresh entity + fresh subscription
        // manager, same uuid.
        EventSubscriptionManager gen2Subs = mock(EventSubscriptionManager.class);
        List<EventAction<?>> gen2Subscribed = captureSubscriptions(gen2Subs);
        ActiveAbility gen2Ctx = newCtx(gen2Subs);
        HunterBowBehavior gen2 = new HunterBowBehavior(gen2Ctx);
        // Pre-fix this was 2: the stale count survived logout, so the isFirst
        // gate blocked every later activation.
        assertEquals(1, HunterBowBehavior.refCountForTest(uuid));

        gen2.onActivate(gen2Ctx);
        // THE regression assertion: pre-fix code subscribed nothing here.
        verify(gen2Subs, times(3)).subscribe(any());
        assertNotNull(quitActionOf(gen2Subscribed), "quit handler re-armed");
    }
}
