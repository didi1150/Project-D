package dev.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.DefaultStats;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.impl.ResourceStat;

/**
 * Covers the dynamic cost formula language: arithmetic parsing/precedence,
 * variable resolution against caster stats (including the MISSING aliases),
 * and the error paths (bad syntax, unknown variables, non-finite results).
 */
class CostFormulaTest {

    @Test
    void additionAndPrecedence() {
        assertEquals(14, CostFormula.parse("2 + 3 * 4").evaluate(entity()));
    }

    @Test
    void parenthesesOverridePrecedence() {
        assertEquals(20, CostFormula.parse("(2 + 3) * 4").evaluate(entity()));
    }

    @Test
    void divisionAndSubtraction() {
        assertEquals(2.5, CostFormula.parse("10 / 4").evaluate(entity()));
        assertEquals(5, CostFormula.parse("10 - 5").evaluate(entity()));
    }

    @Test
    void unaryMinus() {
        assertEquals(5, CostFormula.parse("-5 + 10").evaluate(entity()));
        assertEquals(-6, CostFormula.parse("2 * -(1 + 2)").evaluate(entity()));
    }

    @Test
    void whitespaceTolerant() {
        assertEquals(7, CostFormula.parse("  3+4 ").evaluate(entity()));
    }

    @Test
    void statVariablesResolveAgainstCaster() {
        RPGEntity caster = entity();
        assertEquals(100, CostFormula.parse("MANA_MAX").evaluate(caster));
        assertEquals(15, CostFormula.parse("0.15 * MANA_MAX").evaluate(caster));
        assertEquals(30, CostFormula.parse("ABILITY_POWER + ARMOR").evaluate(caster));
    }

    @Test
    void variablesAreCaseInsensitive() {
        assertEquals(100, CostFormula.parse("mana_max").evaluate(entity()));
    }

    @Test
    void missingResourceAliasIsMaxMinusCurrent() {
        RPGEntity caster = entity();
        caster.setMana(40);
        assertEquals(60, CostFormula.parse("MANA_MISSING").evaluate(caster));
    }

    @Test
    void unknownVariableThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CostFormula.parse("GOLD + 1").evaluate(entity()));
        assertTrue(e.getMessage().contains("GOLD"));
    }

    @Test
    void syntaxErrorsThrowAtParseTime() {
        assertThrows(IllegalArgumentException.class, () -> CostFormula.parse("10 +"));
        assertThrows(IllegalArgumentException.class, () -> CostFormula.parse("(10 + 5"));
        assertThrows(IllegalArgumentException.class, () -> CostFormula.parse("10 & 5"));
        assertThrows(IllegalArgumentException.class, () -> CostFormula.parse("10 5"));
        assertThrows(IllegalArgumentException.class, () -> CostFormula.parse(""));
        assertThrows(IllegalArgumentException.class, () -> CostFormula.parse(null));
    }

    @Test
    void nonFiniteResultThrows() {
        assertThrows(IllegalArgumentException.class, () -> CostFormula.parse("1 / 0").evaluate(entity()));
    }

    @Test
    void sourceIsPreserved() {
        assertEquals("10 + 0.05 * MANA_MAX", CostFormula.parse("10 + 0.05 * MANA_MAX").getSource());
    }

    private RPGEntity entity() {
        StatManager stats = new StatManager(DefaultStats.getDefaultStats());
        stats.addAll(java.util.Map.of(
                StatType.MANA_MAX, new CombatStat("MANA_MAX", 100),
                StatType.ABILITY_POWER, new CombatStat("ABILITY_POWER", 10),
                StatType.ARMOR, new CombatStat("ARMOR", 20),
                StatType.MANA_RESOURCE,
                new ResourceStat("MANA_RESOURCE", n -> 100d, n -> 0.0, System.currentTimeMillis())));
        return new TestRPGEntity(stats);
    }

    private static final class TestRPGEntity extends RPGEntity {
        TestRPGEntity(StatManager stats) {
            super(stats, UUID.randomUUID(), "formula-test", EntityType.MOB, null, null, RPGClassType.NONE);
        }
    }
}
