package dev.bukkit.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * {@link StealthRegistry#clearAggro} / {@link StealthRegistry#detachMob} matrix.
 * Zombified piglins are the special case: they re-acquire targets out of the
 * Universal Anger memory even while their target is null, so detaching one must
 * also defuse its anger or the shroud sweep cannot hold them off the player.
 */
class StealthRegistryClearAggroTest {

    @Test
    void zombifiedPiglinTargetingThePlayerIsDetachedAndCalmed() {
        UUID playerUuid = UUID.randomUUID();
        PigZombie pigman = mobWithTarget(PigZombie.class, playerUuid);

        StealthRegistry.detachMob(pigman, playerUuid);

        verify(pigman).setTarget(null);
        verify(pigman).setAngry(false);
    }

    @Test
    void zombifiedPiglinTargetingSomeoneElseIsLeftAlone() {
        UUID playerUuid = UUID.randomUUID();
        PigZombie pigman = mobWithTarget(PigZombie.class, UUID.randomUUID());

        StealthRegistry.detachMob(pigman, playerUuid);

        // the target read is expected; nothing may be mutated
        verify(pigman).getTarget();
        verifyNoMoreInteractions(pigman);
    }

    @Test
    void zombifiedPiglinWithoutATargetIsLeftAlone() {
        UUID playerUuid = UUID.randomUUID();
        PigZombie pigman = mock(PigZombie.class);
        when(pigman.getTarget()).thenReturn(null);

        StealthRegistry.detachMob(pigman, playerUuid);

        verify(pigman).getTarget();
        verifyNoMoreInteractions(pigman);
    }

    @Test
    void classicGoalMobsAreOnlyDetached() {
        UUID playerUuid = UUID.randomUUID();
        Zombie zombie = mobWithTarget(Zombie.class, playerUuid);

        StealthRegistry.detachMob(zombie, playerUuid);

        verify(zombie).setTarget(null);
        verify(zombie).getTarget();
        verifyNoMoreInteractions(zombie);
    }

    @Test
    void clearAggroSweepsEveryWorldAndOnlyTouchesMobsOnTheCaster() {
        UUID playerUuid = UUID.randomUUID();

        PigZombie pigman = mobWithTarget(PigZombie.class, playerUuid);
        Zombie unrelated = mobWithTarget(Zombie.class, UUID.randomUUID());
        World world = mock(World.class);
        when(world.getEntitiesByClass(Mob.class)).thenReturn(List.of(pigman, unrelated));

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));

            assertDoesNotThrow(() -> StealthRegistry.clearAggro(player));
        }
        verify(pigman).setTarget(null);
        verify(pigman).setAngry(false);
        verify(unrelated).getTarget();
        verifyNoMoreInteractions(unrelated);
    }

    private static <T extends Mob> T mobWithTarget(Class<T> mobClass, UUID targetUuid) {
        T mob = mock(mobClass);
        LivingEntity target = mock(LivingEntity.class);
        when(target.getUniqueId()).thenReturn(targetUuid);
        when(mob.getTarget()).thenReturn(target);
        return mob;
    }
}
