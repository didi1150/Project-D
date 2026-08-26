package dev.bukkit.ability.behavior;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.utils.StealthRegistry;
import dev.core.ability.ActiveAbility;
import dev.core.event.EventAction;
import dev.core.event.EventSubscriptionManager;

/**
 * Orb stealth × THREAT set interaction: a shrouded tank must never pull aggro
 * onto themselves when a mob retargets, while an unshrouded tank still can.
 */
class ThreatBehaviorStealthTest {

    private World world;
    private UUID tankId;
    private Player tank;
    private Mob mob;
    private Player originalTarget;
    private EntityTargetLivingEntityEvent event;
    private EventAction<EntityTargetLivingEntityEvent> onTargetAction;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        world = mock(World.class);
        tankId = UUID.randomUUID();

        tank = mock(Player.class);
        when(tank.getUniqueId()).thenReturn(tankId);
        // 5 blocks from the mob (below), inside both aggro range and own cloud
        when(tank.getLocation()).thenReturn(new Location(world, 3, 64, 4));
        when(tank.getWorld()).thenReturn(world);
        when(tank.isDead()).thenReturn(false);
        when(tank.isOnline()).thenReturn(true);

        originalTarget = mock(Player.class);
        when(originalTarget.getUniqueId()).thenReturn(UUID.randomUUID());

        mob = mock(Mob.class);
        when(mob.getWorld()).thenReturn(world);
        when(mob.getLocation()).thenReturn(new Location(world, 0, 64, 0));

        event = mock(EntityTargetLivingEntityEvent.class);
        when(event.getEntity()).thenReturn(mob);
        when(event.getTarget()).thenReturn(originalTarget);

        BukkitPlayerEntity holder = mock(BukkitPlayerEntity.class);
        when(holder.getPlayer()).thenReturn(Optional.of(tank));
        when(holder.getUuid()).thenReturn(tankId);

        ActiveAbility ctx = mock(ActiveAbility.class);
        when(ctx.getHolder()).thenReturn(holder);
        EventSubscriptionManager subscriptions = mock(EventSubscriptionManager.class);
        when(ctx.getSubscriptions()).thenReturn(subscriptions);

        AtomicReference<EventAction<?>> captured = new AtomicReference<>();
        doAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return null;
        }).when(subscriptions).subscribe(any());

        new ThreatBehavior(ctx).onActivate(ctx);

        onTargetAction = (EventAction<EntityTargetLivingEntityEvent>) captured.get();
        assertNotNull(onTargetAction, "onActivate must subscribe the target handler");
    }

    @AfterEach
    void tearDown() {
        StealthRegistry.clearAll(tankId);
    }

    @Test
    void shroudedTankNeverPullsAggro() {
        StealthRegistry.placeShroud(tankId, new Location(world, 3, 64, 4), 7.0, 6000);

        for (int i = 0; i < 200; i++) {
            onTargetAction.execute(event);
        }

        verify(event, never()).setTarget(any());
    }

    @Test
    void unshroudedTankCanStillPullAggro() {
        // no shroud: the 50% reroll must land at least once across 200 attempts
        // (p(miss every time) ≈ 6e-61)
        for (int i = 0; i < 200; i++) {
            onTargetAction.execute(event);
        }

        verify(event, atLeastOnce()).setTarget(tank);
    }
}
