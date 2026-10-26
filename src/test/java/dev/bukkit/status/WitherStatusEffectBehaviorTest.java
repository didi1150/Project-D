package dev.bukkit.status;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.status.behavior.WitherStatusEffectBehavior;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityManager;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.loader.StatLoader;
import dev.core.status.ActiveStatusEffect;
import dev.core.status.NoopStatusEffectManager;
import dev.core.status.StatusEffectType;

class WitherStatusEffectBehaviorTest {

    private LivingEntity living;
    private RPGEntity target;
    private WitherStatusEffectBehavior behavior;

    @BeforeEach
    void setUp() {
        EntityManager.getInstance().clear();
        UUID uuid = UUID.randomUUID();

        Map<StatType, Stat> base = new HashMap<>();
        base.put(StatType.HEALTH_MAX, new CombatStat("HEALTH_MAX", 100));
        base.put(StatType.MAGIC_RESIST, new CombatStat("MAGIC_RESIST", 0));
        StatManager stats = new StatManager(StatLoader.copyStats(base));
        target = new RPGEntity(stats, uuid, "test", EntityType.MOB, mock(EffectManagerInterface.class),
                BukkitEventBus.getInstance(), RPGClassType.NONE, NoopStatusEffectManager.getInstance()) {
        };
        EntityManager.getInstance().registerEntity(target);

        World world = mock(World.class);
        Location location = mock(Location.class);
        when(location.clone()).thenReturn(location);
        when(location.add(anyDouble(), anyDouble(), anyDouble())).thenReturn(location);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        living = mock(LivingEntity.class);
        when(living.getUniqueId()).thenReturn(uuid);
        when(living.isValid()).thenReturn(true);
        when(living.isDead()).thenReturn(false);
        when(living.getWorld()).thenReturn(world);
        when(living.getLocation()).thenReturn(location);
        when(living.getHeight()).thenReturn(1.8);
        when(living.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(false);

        behavior = new WitherStatusEffectBehavior();
    }

    @AfterEach
    void tearDown() {
        EntityManager.getInstance().clear();
    }

    @Test
    void damageFlowsThroughTheRpgPipelineInsteadOfVanillaHealth() {
        ActiveStatusEffect effect = new ActiveStatusEffect(StatusEffectType.WITHER,
                System.currentTimeMillis(), System.currentTimeMillis() + 10_000, true, 10.0, null);
        StatusEffectContext context = new StatusEffectContext(target, living, effect);

        double before = target.getHealth();
        behavior.onApply(context);
        for (int i = 0; i < 20; i++) {
            behavior.onTick(context, System.currentTimeMillis());
        }

        assertTrue(target.getHealth() < before);
        verify(living, never()).setHealth(anyDouble());
    }
}
