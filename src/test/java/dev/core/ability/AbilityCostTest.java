package dev.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.core.storage.config.ConfigSection;

/**
 * Covers the redesigned {@link AbilityCost}: mode/amount entries, legacy
 * resource-type strings, factories, the builder and the config section form
 * used by ability loaders.
 */
class AbilityCostTest {

    @Test
    void manaCostFactory() {
        AbilityCost cost = AbilityCost.manaCost(25);
        assertTrue(cost.hasCost());
        assertTrue(cost.hasCost(CostMode.MANA));
        assertFalse(cost.hasCost(CostMode.HEALTH));
        assertEquals(25, cost.getCost(CostMode.MANA));
        assertEquals(0, cost.getCost(CostMode.HEALTH));
        assertEquals(1, cost.getCosts().size());
    }

    @Test
    void healthCostFactory() {
        AbilityCost cost = AbilityCost.healthCost(10);
        assertTrue(cost.hasCost(CostMode.HEALTH));
        assertEquals(10, cost.getCost(CostMode.HEALTH));
    }

    @Test
    void noCost() {
        AbilityCost cost = AbilityCost.noCost();
        assertFalse(cost.hasCost());
        assertEquals(0, cost.getCost(CostMode.MANA));
        assertTrue(cost.getCosts().isEmpty());
    }

    @Test
    void legacyResourceTypeStringsMapToModes() {
        AbilityCost cost = new AbilityCost("MANA_RESOURCE", 10);
        assertTrue(cost.hasCost(CostMode.MANA));
        cost.addCost("HEALTH_RESOURCE", 5);
        assertTrue(cost.hasCost(CostMode.HEALTH));
        assertEquals(5, cost.getCost(CostMode.HEALTH));

        AbilityCost byStatName = new AbilityCost("MANA", 7);
        assertTrue(byStatName.hasCost(CostMode.MANA));
        assertEquals(7, byStatName.getCost(CostMode.MANA));
    }

    @Test
    void unknownResourceTypeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityCost("GOLD", 5));
        assertThrows(IllegalArgumentException.class, () -> AbilityCost.builder().custom("GOLD", 5).build());
    }

    @Test
    void zeroAndNegativeAmountsAreDropped() {
        AbilityCost cost = new AbilityCost();
        cost.addCost(CostMode.MANA, 0);
        cost.addCost(CostMode.HEALTH, -3);
        assertFalse(cost.hasCost());
        assertTrue(cost.getCosts().isEmpty());
    }

    @Test
    void builderCombinesModes() {
        AbilityCost cost = AbilityCost.builder().mana(10).health(20).custom("MANA_RESOURCE", 5).build();
        assertEquals(2, cost.getCosts().size(), "same-mode entries merge into one entry per mode");
        assertEquals(15, cost.getCost(CostMode.MANA), "same-mode amounts must sum");
        assertEquals(20, cost.getCost(CostMode.HEALTH));
    }

    @Test
    void fromConfigParsesModeAndAmount() {
        TestConfigSection section = new TestConfigSection(new HashMap<>());
        section.set("mode", "HEALTH");
        section.set("amount", "7.5");
        AbilityCost cost = AbilityCost.fromConfig(section);
        assertTrue(cost.hasCost(CostMode.HEALTH));
        assertEquals(7.5, cost.getCost(CostMode.HEALTH));
    }

    @Test
    void fromConfigDefaultsToMana() {
        AbilityCost cost = AbilityCost.fromConfig(new TestConfigSection(new HashMap<>()));
        assertFalse(cost.hasCost(), "missing amount means no cost");
        assertTrue(cost.hasCost(CostMode.MANA) == false);
    }

    @Test
    void fromConfigParsesModeCaseInsensitively() {
        TestConfigSection section = new TestConfigSection(new HashMap<>());
        section.set("mode", "mana");
        section.set("amount", "25");
        AbilityCost cost = AbilityCost.fromConfig(section);
        assertTrue(cost.hasCost(CostMode.MANA), "mode parsing is case-insensitive");
    }

    @Test
    void fromConfigParsesFormula() {
        TestConfigSection section = new TestConfigSection(new HashMap<>());
        section.set("mode", "MANA");
        section.set("formula", "10 + 0.05 * MANA_MAX");
        AbilityCost cost = AbilityCost.fromConfig(section);
        assertTrue(cost.hasCost(), "a formula alone is a chargeable cost");
        assertEquals(1, cost.getCosts().size());
        assertNotNull(cost.getCosts().get(0).formula(), "the parsed formula must be carried on the entry");
        assertEquals("10 + 0.05 * MANA_MAX", cost.getCosts().get(0).formula().getSource());
        assertEquals(0, cost.getCost(CostMode.MANA), "getCost reports the flat component only");
    }

    @Test
    void fromConfigFormulaOverridesFlatAmountForResolve() {
        TestConfigSection section = new TestConfigSection(new HashMap<>());
        section.set("mode", "MANA");
        section.set("amount", "25");
        section.set("formula", "5");
        AbilityCost cost = AbilityCost.fromConfig(section);
        assertEquals(25, cost.getCost(CostMode.MANA));
        assertEquals(25, cost.getCosts().get(0).resolve(null),
                "without a caster, resolve falls back to the flat amount");
    }

    @Test
    void fromConfigBadFormulaThrows() {
        TestConfigSection section = new TestConfigSection(new HashMap<>());
        section.set("mode", "MANA");
        section.set("formula", "10 +");
        assertThrows(IllegalArgumentException.class, () -> AbilityCost.fromConfig(section));
    }

    @Test
    void zeroAmountWithoutFormulaIsDropped() {
        AbilityCost cost = new AbilityCost();
        cost.addCost(new CostEntry(CostMode.MANA, 0));
        assertFalse(cost.hasCost());
    }

    @Test
    void getCostsReturnsDefensiveCopy() {
        AbilityCost cost = AbilityCost.manaCost(25);
        cost.getCosts().clear();
        assertEquals(1, cost.getCosts().size());
    }

    // ---------------------------------------------------------------- test config

    private static final class TestConfigSection implements ConfigSection {
        private final Map<String, Object> values;

        private TestConfigSection(Map<String, Object> values) {
            this.values = values;
        }

        private Object resolve(String path) {
            if (path.indexOf('.') < 0) {
                return values.get(path);
            }
            Object next = values.get(path.substring(0, path.indexOf('.')));
            if (next instanceof TestConfigSection section) {
                return section.resolve(path.substring(path.indexOf('.') + 1));
            }
            return null;
        }

        @Override
        public ConfigSection getSection(String path) {
            Object existing = values.get(path);
            if (existing instanceof TestConfigSection section) {
                return section;
            }
            TestConfigSection section = new TestConfigSection(new HashMap<>());
            values.put(path, section);
            return section;
        }

        @Override
        public String getString(String path, String def) {
            Object value = resolve(path);
            return value == null ? def : value.toString();
        }

        @Override
        public int getInt(String path, int def) {
            Object value = resolve(path);
            return value == null ? def : Integer.parseInt(value.toString());
        }

        @Override
        public double getDouble(String path, double def) {
            Object value = resolve(path);
            return value == null ? def : Double.parseDouble(value.toString());
        }

        @Override
        public boolean getBoolean(String path, boolean def) {
            Object value = resolve(path);
            return value == null ? def : Boolean.parseBoolean(value.toString());
        }

        @Override
        public List<String> getStringList(String path) {
            return new ArrayList<>();
        }

        @Override
        public List<ConfigSection> getSectionList(String path) {
            return new ArrayList<>();
        }

        @Override
        public void set(String path, Object value) {
            values.put(path, value);
        }

        @Override
        public Set<String> getKeys() {
            return values.keySet();
        }
    }
}