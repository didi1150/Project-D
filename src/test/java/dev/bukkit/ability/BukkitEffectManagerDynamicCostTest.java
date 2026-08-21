package dev.bukkit.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.ability.Ability;
import dev.core.ability.AbilityCost;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.DefaultStats;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.impl.ResourceStat;

/**
 * Drives the real {@link BukkitEffectManager} with formula-driven ability
 * costs: a dynamic cost gates the cast on the caster's current stats, is
 * deducted at its resolved value, and an unresolvable formula refuses the cast
 * without draining anything.
 */
class BukkitEffectManagerDynamicCostTest {

    private static final double MANA_MAX = 100;

    @BeforeEach
    void setUp() {
        AbilityRegistry.clear();
        AbilityRegistry.register(ability("DYNAMIC_CHEAP", "0.1 * MANA_MAX"), NoopEffect::new);
        AbilityRegistry.register(ability("DYNAMIC_EXPENSIVE", "MANA_MAX"), NoopEffect::new);
        AbilityRegistry.register(ability("DYNAMIC_BROKEN", "NOT_A_STAT + 1"), NoopEffect::new);
    }

    @Test
    void formulaCostIsResolvedAndDeductedAtCastTime() {
        RPGEntity entity = entity(50);
        Ability ability = AbilityRegistry.get("DYNAMIC_CHEAP").orElseThrow();

        Effect effect = manager().cast(entity, ability);

        assertNotNull(effect, "10% of 100 max mana is affordable at 50 mana");
        assertEquals(40, entity.getMana(), "the resolved formula price (10) must be deducted");
    }

    @Test
    void unaffordableFormulaCostRefusesCast() {
        RPGEntity entity = entity(50);
        Ability ability = AbilityRegistry.get("DYNAMIC_EXPENSIVE").orElseThrow();

        assertNull(manager().cast(entity, ability), "a 100-mana formula cost is not payable at 50 mana");
        assertEquals(50, entity.getMana(), "a refused cast must not drain resources");
    }

    @Test
    void unknownFormulaVariableRefusesCastWithoutDraining() {
        RPGEntity entity = entity(50);
        Ability ability = AbilityRegistry.get("DYNAMIC_BROKEN").orElseThrow();

        assertNull(manager().cast(entity, ability), "an unresolvable formula must refuse the cast");
        assertEquals(50, entity.getMana(), "a failed cost evaluation must not drain resources");
    }

    private dev.core.ability.EffectManagerInterface manager() {
        return BukkitEffectManager.getInstance();
    }

    private static Ability ability(String id, String formula) {
        Ability ability = new StubAbility(id);
        ability.setCost(AbilityCost.fromConfig(new StaticFormulaSection(formula)));
        return ability;
    }

    private static RPGEntity entity(double mana) {
        StatManager stats = new StatManager(DefaultStats.getDefaultStats());
        stats.addAll(java.util.Map.of(
                StatType.MANA_MAX, new CombatStat("MANA_MAX", MANA_MAX),
                StatType.MANA_RESOURCE,
                new ResourceStat("MANA_RESOURCE", n -> MANA_MAX, n -> 0.0, System.currentTimeMillis())));
        RPGEntity entity = new TestRPGEntity(stats);
        entity.setMana(mana);
        return entity;
    }

    /** Minimal config section exposing only {@code formula}. */
    private static final class StaticFormulaSection implements dev.core.storage.config.ConfigSection {
        private final String formula;

        StaticFormulaSection(String formula) {
            this.formula = formula;
        }

        @Override
        public String getString(String path, String def) {
            return "formula".equals(path) ? formula : def;
        }

        @Override
        public int getInt(String path, int def) {
            return def;
        }

        @Override
        public double getDouble(String path, double def) {
            return def;
        }

        @Override
        public boolean getBoolean(String path, boolean def) {
            return def;
        }

        @Override
        public java.util.List<String> getStringList(String path) {
            return java.util.List.of();
        }

        @Override
        public dev.core.storage.config.ConfigSection getSection(String path) {
            return null;
        }

        @Override
        public java.util.List<dev.core.storage.config.ConfigSection> getSectionList(String path) {
            return java.util.List.of();
        }

        @Override
        public void set(String path, Object value) {
        }

        @Override
        public java.util.Set<String> getKeys() {
            return java.util.Set.of("formula");
        }
    }

    private static final class StubAbility extends Ability {
        StubAbility(String id) {
            super(id);
        }
    }

    private static final class NoopEffect extends Effect {
        NoopEffect(String cooldownKey) {
            super(null, 1, false, cooldownKey);
        }

        @Override
        public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        }

        @Override
        public void cancel() {
        }
    }

    private static final class TestRPGEntity extends RPGEntity {
        TestRPGEntity(StatManager stats) {
            super(stats, UUID.randomUUID(), "dynamic-cost-test", EntityType.MOB,
                    BukkitEffectManager.getInstance(), null, RPGClassType.NONE);
        }
    }
}
