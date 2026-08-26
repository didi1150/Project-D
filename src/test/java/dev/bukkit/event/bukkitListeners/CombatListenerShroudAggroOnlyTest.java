package dev.bukkit.event.bukkitListeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import dev.bukkit.item.BowArrowManager;
import dev.bukkit.utils.StealthRegistry;

/**
 * The shroud blocks AGGRO only, never damage: a shrouded player is
 * untargetable, but any hostile hit that still connects — AoE splash, an
 * already-swung melee, a mob-shot projectile, an explosion — lands normally.
 * These tests pin that no-shield behavior so a damage-cancel cannot creep
 * back in.
 *
 * <p>
 * A passing hit falls through into the RPG/vanilla pipeline, which touches
 * server-only code (damage indicators), so {@link Bukkit} is statically
 * mocked and the spawned indicator display is a mock.
 */
class CombatListenerShroudAggroOnlyTest {

    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private MockedStatic<Bukkit> bukkitStatic;
    private CombatListener listener;
    private World world;
    private UUID victimId;
    private Player victim;

    @BeforeEach
    void setUp() {
        StealthRegistry.CLOCK = clock::get;
        bukkitStatic = Mockito.mockStatic(Bukkit.class);
        bukkitStatic.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
        listener = new CombatListener(mock(org.bukkit.plugin.Plugin.class));
        world = mock(World.class);
        TextDisplay indicator = mock(TextDisplay.class);
        when(indicator.getLocation()).thenReturn(new Location(world, 0, 66, 0));
        when(world.spawn(any(Location.class), any(Class.class), any())).thenReturn(indicator);

        victimId = UUID.randomUUID();
        victim = mock(Player.class);
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        // SoulSkull guard reads the victim's PDC before anything else
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(any(), any())).thenReturn(false);
        when(victim.getPersistentDataContainer()).thenReturn(pdc);
    }

    @AfterEach
    void tearDown() {
        bukkitStatic.close();
        StealthRegistry.clearAll(victimId);
        StealthRegistry.CLOCK = System::currentTimeMillis;
    }

    /** A victim hit by the given damager; read the outcome via {@code event.isCancelled()}. */
    private EntityDamageByEntityEvent hitBy(org.bukkit.entity.Entity damager, double damage) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamager()).thenReturn(damager);
        when(event.getDamage()).thenReturn(damage);
        // the RPG pipeline looks up both parties by uuid once it runs
        if (damager != null) {
            when(damager.getUniqueId()).thenReturn(UUID.randomUUID());
        }
        trackCancellation(event);
        return event;
    }

    /** Back any {@code isCancelled()} readout with the mock's own setCancelled calls. */
    private void trackCancellation(EntityDamageEvent event) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        doAnswer(inv -> {
            cancelled.set(true);
            return null;
        }).when(event).setCancelled(anyBoolean());
        when(event.isCancelled()).thenAnswer(inv -> cancelled.get());
    }

    private Mob zombie() {
        return mock(Mob.class);
    }

    private Projectile arrowShotBy(ProjectileSource shooter) {
        Projectile arrow = mock(Projectile.class);
        when(arrow.getShooter()).thenReturn(shooter);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.get(BowArrowManager.ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE)).thenReturn(null);
        when(arrow.getPersistentDataContainer()).thenReturn(pdc);
        return arrow;
    }

    private void placeCloudAroundPlayer() {
        StealthRegistry.placeShroud(victimId, new Location(world, 0, 64, 0), 7.0, 6000);
    }

    @Test
    void zombieHitOnShroudedPlayerLands() {
        placeCloudAroundPlayer();

        EntityDamageByEntityEvent event = hitBy(zombie(), 5.0);
        listener.onDamagedByEntity(event);
        assertFalse(event.isCancelled(), "shroud must not shield against damage — aggro only");
    }

    @Test
    void skeletonArrowAtShroudedPlayerLands() {
        placeCloudAroundPlayer();
        LivingEntity skeleton = mock(LivingEntity.class,
                Mockito.withSettings().extraInterfaces(ProjectileSource.class));
        // projectile scaling looks the shooter up in EntityManager by uuid
        when(skeleton.getUniqueId()).thenReturn(UUID.randomUUID());

        EntityDamageByEntityEvent event = hitBy(arrowShotBy(skeleton), 4.0);
        listener.onDamagedByEntity(event);
        assertFalse(event.isCancelled(), "mob-shot projectiles are not blocked by the shroud either");
    }

    @Test
    void explosionWhileShroudedLands() {
        placeCloudAroundPlayer();

        EntityDamageEvent explosion = mock(EntityDamageEvent.class);
        when(explosion.getEntity()).thenReturn(victim);
        when(explosion.getCause()).thenReturn(DamageCause.ENTITY_EXPLOSION);
        when(explosion.getDamage()).thenReturn(9.0);
        trackCancellation(explosion);
        listener.onDamage(explosion);
        assertFalse(explosion.isCancelled(),
                "AoE that never targeted you still lands while shrouded");
    }

    @Test
    void environmentalDamageWhileShroudedIsNotBlocked() {
        placeCloudAroundPlayer();

        EntityDamageEvent lava = mock(EntityDamageEvent.class);
        when(lava.getEntity()).thenReturn(victim);
        when(lava.getCause()).thenReturn(DamageCause.LAVA);
        when(lava.getDamage()).thenReturn(3.0);
        trackCancellation(lava);
        listener.onDamage(lava);
        assertFalse(lava.isCancelled(), "the shroud never grants environment immunity");
    }
}
