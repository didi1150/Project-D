package dev.bukkit.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.ability.Ability;
import dev.core.ability.CooldownScaling;
import dev.core.ability.CooldownSink;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.DefaultStats;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;

/**
 * Cooldown tests for the manager + {@link BukkitCooldownSink}: haste scaling,
 * the NONE (no-scaling) category, custom raw durations and clearing. Uses the
 * real singleton manager with a fake (non-Bukkit) entity, mirroring
 * {@link BukkitEffectManagerCastSmokeTest}.
 */
class BukkitEffectManagerCooldownTest {

    private static final long BASE_COOLDOWN = 3000;

    private BukkitEffectManager manager;

    @BeforeEach
    void setUp() {
        manager = (BukkitEffectManager) BukkitEffectManager.getInstance();
    }

    @Test
    void remainingCooldownIsZeroBeforeStart() {
        RPGEntity entity = entity(0);
        CooldownSink sink = sink(entity, ability(BASE_COOLDOWN, CooldownScaling.HASTE));

        assertEquals(0, sink.remainingCooldown());
    }

    @Test
    void hasteScalingShortensCooldown() {
        // 100 haste => base * 100 / (100 + 100) = half the cooldown.
        RPGEntity entity = entity(100);
        CooldownSink sink = sink(entity, ability(BASE_COOLDOWN, CooldownScaling.HASTE));

        sink.startCooldown();

        long remaining = sink.remainingCooldown();
        assertTrue(remaining > 0 && remaining <= BASE_COOLDOWN / 2,
                "expected remaining <= " + BASE_COOLDOWN / 2 + " with 100 haste, got " + remaining);
    }

    @Test
    void noScalingKeepsConfiguredCooldown() {
        // Even with haste, NONE abilities keep the raw configured cooldown
        // (a no-scaling ability's category).
        RPGEntity entity = entity(100);
        CooldownSink sink = sink(entity, ability(BASE_COOLDOWN, CooldownScaling.NONE));

        sink.startCooldown();

        long remaining = sink.remainingCooldown();
        assertTrue(remaining > BASE_COOLDOWN - 50 && remaining <= BASE_COOLDOWN,
                "expected remaining near " + BASE_COOLDOWN + ", got " + remaining);
    }

    @Test
    void customMillisIgnoresScaling() {
        RPGEntity entity = entity(100);
        CooldownSink sink = sink(entity, ability(BASE_COOLDOWN, CooldownScaling.HASTE));

        sink.startCooldown(2500);

        long remaining = sink.remainingCooldown();
        assertTrue(remaining > 2450 && remaining <= 2500,
                "expected raw custom duration near 2500, got " + remaining);
    }

    @Test
    void clearCooldownRemovesRunningCooldown() {
        RPGEntity entity = entity(0);
        CooldownSink sink = sink(entity, ability(BASE_COOLDOWN, CooldownScaling.HASTE));

        sink.startCooldown();
        assertTrue(sink.remainingCooldown() > 0);

        sink.clearCooldown();

        assertEquals(0, sink.remainingCooldown());
    }

    @Test
    void defaultScalingIsHaste() {
        Ability ability = new StubAbility("COOLDOWN_TEST");
        assertEquals(CooldownScaling.HASTE, ability.getCooldownScaling());
    }

    private CooldownSink sink(RPGEntity entity, Ability ability) {
        return new BukkitCooldownSink(manager, entity, ability, "cooldown-test-key");
    }

    private Ability ability(long cooldown, CooldownScaling scaling) {
        Ability ability = new StubAbility("COOLDOWN_TEST");
        ability.setCooldown(cooldown);
        ability.setCooldownScaling(scaling);
        return ability;
    }

    private RPGEntity entity(double haste) {
        StatManager stats = new StatManager(DefaultStats.getDefaultStats());
        stats.addAll(Map.of(StatType.ABILITY_HASTE, new CombatStat("ABILITY_HASTE", haste)));
        return new TestRPGEntity(stats, UUID.randomUUID());
    }

    private static final class StubAbility extends Ability {
        StubAbility(String id) {
            super(id);
        }
    }

    private static final class TestRPGEntity extends RPGEntity {
        TestRPGEntity(StatManager stats, UUID uuid) {
            super(stats, uuid, "cooldown-test", EntityType.MOB, BukkitEffectManager.getInstance(), null,
                    RPGClassType.NONE);
        }
    }
}