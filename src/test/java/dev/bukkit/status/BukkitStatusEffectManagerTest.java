package dev.bukkit.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import dev.bukkit.status.behavior.AbsorptionStatusEffectBehavior;
import dev.bukkit.status.behavior.AirborneStatusEffectBehavior;
import dev.bukkit.status.behavior.RootedStatusEffectBehavior;
import dev.bukkit.status.behavior.SlowedStatusEffectBehavior;
import dev.bukkit.status.behavior.StunnedStatusEffectBehavior;
import dev.core.entity.EntityManager;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.status.ActiveStatusEffect;
import dev.core.status.StatusEffectType;
import dev.core.utils.AdventureText;

/**
 * Headless smoke test for the Bukkit status manager: display spawn/stacking and
 * removal, behavior dispatch (stat-engine slow, potion fallback, AI freeze),
 * and CC-immunity rejection through the real singleton machinery. The vanilla
 * entity is mocked and {@code resolveLiving} is overridden (the server registry
 * cannot resolve entities during tests).
 */
class BukkitStatusEffectManagerTest {

    private LivingEntity living;
    private World world;
    private TextDisplay display;
    private Location location;
    private TestStatusEffectManager manager;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        living = mock(LivingEntity.class);
        world = mock(World.class);
        display = mock(TextDisplay.class);
        location = mock(Location.class);
        when(display.isValid()).thenReturn(true);
        when(display.text()).thenReturn(Component.text("stub"));
        when(display.getTransformation())
                .thenReturn(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(), new Quaternionf()));
        when(living.getWorld()).thenReturn(world);
        when(living.getHeight()).thenReturn(1.8);
        // Each spawn call adds its own vertical offset: return a fresh Location
        // carrying the requested y so stacking tests can compare offsets.
        when(location.add(anyDouble(), anyDouble(), anyDouble())).thenAnswer(inv -> {
            double y = inv.getArgument(1);
            return new Location(world, 0, y, 0);
        });
        when(living.getLocation()).thenReturn(location);
        // Run the spawn consumer so the display actually gets configured
        // (text, billboard, transformation) like on a live server.
        when(world.spawn(any(), eq(TextDisplay.class), any(Consumer.class))).thenAnswer(inv -> {
            Consumer<TextDisplay> consumer = inv.getArgument(2);
            consumer.accept(display);
            return display;
        });
        manager = new TestStatusEffectManager(living);
        registerBehaviors();
    }

    @AfterEach
    void tearDown() {
        manager.cancelAll();
        EntityManager.getInstance().clear();
    }

    private static void registerBehaviors() {
        StatusEffectBehaviorRegistry.register(StatusEffectType.SLOWED, new SlowedStatusEffectBehavior());
        StatusEffectBehaviorRegistry.register(StatusEffectType.ROOTED, new RootedStatusEffectBehavior());
        StatusEffectBehaviorRegistry.register(StatusEffectType.STUNNED, new StunnedStatusEffectBehavior());
        StatusEffectBehaviorRegistry.register(StatusEffectType.AIRBORNE, new AirborneStatusEffectBehavior());
        StatusEffectBehaviorRegistry.register(StatusEffectType.ABSORPTION, new AbsorptionStatusEffectBehavior());
    }

    @SuppressWarnings("unchecked")
    @Test
    void applySpawnsTextDisplayAboveTheName() {
        RPGEntity entity = entityWithMoveSpeed();

        assertTrue(manager.apply(entity, StatusEffectType.SLOWED, 10_000));
        verify(world).spawn(any(), eq(TextDisplay.class), any(Consumer.class));
        ArgumentCaptor<Component> text = ArgumentCaptor.forClass(Component.class);
        verify(display, atLeastOnce()).text(text.capture());
        assertTrue(text.getAllValues().stream().map(AdventureText::plainOrEmpty).anyMatch(t -> t.contains("Slowed")),
                "display must show the effect name: " + text.getAllValues());
    }

    @SuppressWarnings("unchecked")
    @Test
    void stackedEffectsGetIncrementallyHigherDisplays() {
        RPGEntity entity = entityWithMoveSpeed();
        manager.apply(entity, StatusEffectType.SLOWED, 10_000);
        manager.apply(entity, StatusEffectType.ROOTED, 10_000);

        ArgumentCaptor<Location> spawnLocations = ArgumentCaptor.forClass(Location.class);
        verify(world, times(2)).spawn(spawnLocations.capture(), eq(TextDisplay.class), any(Consumer.class));
        List<Location> locations = spawnLocations.getAllValues();
        assertEquals(2, locations.size());
        assertTrue(locations.get(1).getY() > locations.get(0).getY(), "the second display must stack above the first");
    }

    @Test
    void absorptionStacksCombineAmountWithoutRefreshingExistingDurations() {
        RPGEntity entity = entityWithoutMoveSpeed();

        assertTrue(manager.applyStacking(entity, StatusEffectType.ABSORPTION, 10_000, true, 20.0));
        long firstEnd = manager.getActive(entity).get(0).getEndTime();

        assertTrue(manager.applyStacking(entity, StatusEffectType.ABSORPTION, 5_000, true, 10.0));
        List<ActiveStatusEffect> active = manager.getActive(entity);

        assertEquals(2, active.size());
        assertEquals(firstEnd, active.get(0).getEndTime(), "the first shield stack must keep its duration");
        assertEquals(30.0, entity.getAbsorptionAmount(), 0.001, "shield amounts must combine");

        manager.remove(entity, StatusEffectType.ABSORPTION);
        assertEquals(0.0, entity.getAbsorptionAmount(), 0.001);
    }

    @Test
    void slowOnRpgEntityDrivesTheStatEngine() {
        RPGEntity entity = entityWithMoveSpeed();

        manager.apply(entity, StatusEffectType.SLOWED, 10_000);
        double slowed = entity.getStatEngineAdapter().getCurrentValue(StatType.MOVE_SPEED, System.currentTimeMillis());
        assertTrue(slowed < 100, "the MOVE_SPEED stat must drop below its base of 100, was " + slowed);
        assertEquals(60.0, slowed, 0.001, "slowed keeps 60% of the base speed");

        manager.remove(entity, StatusEffectType.SLOWED);
        double restored = entity.getStatEngineAdapter().getCurrentValue(StatType.MOVE_SPEED,
                System.currentTimeMillis());
        assertEquals(100.0, restored, 0.001, "removing the slow must restore the engine value");
    }

    @Test
    void entityWithoutMoveSpeedStatSkipsVanillaCcHeadless() {
        // Without a live server the potion fallback is skipped (the behavior's
        // headless guard); the effect itself must still apply and clean up.
        RPGEntity entity = entityWithoutMoveSpeed();

        assertTrue(manager.apply(entity, StatusEffectType.ROOTED, 10_000));
        verify(living, Mockito.never()).addPotionEffect(any());

        manager.remove(entity, StatusEffectType.ROOTED);
        assertFalse(entity.hasStatusEffect(StatusEffectType.ROOTED));
    }

    @Test
    void stunFreezesMobAiAndRestoresItOnEnd() {
        Mob mob = mock(Mob.class);
        when(mob.isAware()).thenReturn(true);
        when(mob.getWorld()).thenReturn(world);
        when(mob.getHeight()).thenReturn(1.8);
        when(mob.getLocation()).thenReturn(location);
        TestStatusEffectManager stunManager = new TestStatusEffectManager(mob);
        RPGEntity entity = entityWithoutMoveSpeed();

        stunManager.apply(entity, StatusEffectType.STUNNED, 10_000);
        verify(mob).setAware(false);
        verify(mob).setVelocity(any());

        stunManager.tick(System.currentTimeMillis());
        verify(mob, atLeastOnce()).setVelocity(any());

        stunManager.remove(entity, StatusEffectType.STUNNED);
        verify(mob).setAware(true);
        stunManager.cancelAll();
    }

    @Test
    void stunBlocksCastingAndAttackingThroughTheEntityApi() {
        RPGEntity entity = entityWithMoveSpeed(new TestStatusEffectManager(living));
        assertTrue(entity.applyStatusEffect(StatusEffectType.STUNNED, 10_000));
        assertFalse(entity.canAttack());
        assertTrue(entity.getStatusEffectManager().hasHardCc(entity));
    }

    @Test
    void abruptEffectRemovesItsDisplayOnExpiry() {
        RPGEntity entity = entityWithMoveSpeed();
        manager.apply(entity, StatusEffectType.STUNNED, 500); // stunned defaults to abrupt

        manager.tick(System.currentTimeMillis() + 1_000);
        verify(display).remove();
    }

    @Test
    void fadeOutEffectStillCleansUpHeadless() {
        RPGEntity entity = entityWithMoveSpeed();
        manager.apply(entity, StatusEffectType.SLOWED, 500, true, 1.0); // explicit fade
        manager.tick(System.currentTimeMillis() + 1_000);
        verify(display).remove();
    }

    @Test
    void ccImmuneCleansesDisplaysAndRejectsNewCc() {
        RPGEntity entity = entityWithMoveSpeed();
        assertTrue(manager.apply(entity, StatusEffectType.SLOWED, 10_000));
        assertTrue(entity.hasStatusEffect(StatusEffectType.SLOWED));

        assertTrue(manager.apply(entity, StatusEffectType.CC_IMMUNE, 10_000));
        assertFalse(entity.hasStatusEffect(StatusEffectType.SLOWED), "immunity must cleanse active CC");
        assertFalse(manager.apply(entity, StatusEffectType.STUNNED, 10_000), "new CC must be rejected");
        assertTrue(manager.isCcImmune(entity));
    }

    private RPGEntity entityWithMoveSpeed() {
        return entityWithMoveSpeed(manager);
    }

    private RPGEntity entityWithMoveSpeed(TestStatusEffectManager statusManager) {
        StatManager stats = new StatManager(new HashMap<>());
        stats.getStats().put(StatType.MOVE_SPEED, new CombatStat("MOVE_SPEED", 100));
        return new TestRPGEntity(stats, statusManager);
    }

    private RPGEntity entityWithoutMoveSpeed() {
        return new TestRPGEntity(new StatManager(new HashMap<>()), manager);
    }

    private static final class TestRPGEntity extends RPGEntity {
        TestRPGEntity(StatManager stats, TestStatusEffectManager statusManager) {
            super(stats, UUID.randomUUID(), "test", EntityType.MOB, null, null, RPGClassType.NONE, statusManager);
            EntityManager.getInstance().registerEntity(this);
        }
    }

    /**
     * Manager bound to a mocked vanilla entity: the server registry can't resolve
     * anything headless, so display/behavior code resolves this mock instead.
     */
    private static final class TestStatusEffectManager extends BukkitStatusEffectManager {
        private final LivingEntity living;

        TestStatusEffectManager(LivingEntity living) {
            this.living = living;
        }

        @Override
        protected LivingEntity resolveLiving(RPGEntity entity) {
            return living;
        }
    }
}
