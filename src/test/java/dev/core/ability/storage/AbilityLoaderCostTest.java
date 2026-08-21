package dev.core.ability.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.ability.Ability;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.CostEntry;
import dev.core.ability.CostMode;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.ability.impl.SoulSummonAbility;
import dev.core.stat.DefaultStats;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/**
 * Covers the {@code cost: { mode, amount | formula }} section of abilities.yml.
 * Costs are config-only (Java never defines them): a configured cost is wired
 * through, an omitted cost means the ability is free, and a malformed cost
 * logs a warning and leaves the ability without a cost.
 */
class AbilityLoaderCostTest {

    @BeforeEach
    void setUp() {
        AbilityRegistry.clear();
        AbilityRegistry.register(new SoulSummonAbility());
    }

    @Test
    void ymlCostDefinesCastCost() {
        TestConfigProvider provider = new TestConfigProvider();
        TestConfigSection soul = root(provider).create("abilities").create("SOUL_SUMMON");
        soul.set("name", "Soul Summon");
        TestConfigSection cost = soul.create("cost");
        cost.set("mode", "MANA");
        cost.set("amount", "40");

        AbilityLoader.loadAll(provider);
        Ability ability = AbilityRegistry.get("SOUL_SUMMON").orElseThrow();
        assertEquals(40, ability.getCost().getCost(CostMode.MANA));
    }

    @Test
    void omittedCostMeansNoCost() {
        TestConfigProvider provider = new TestConfigProvider();
        root(provider).create("abilities").create("SOUL_SUMMON").set("name", "Soul Summon");

        AbilityLoader.loadAll(provider);
        Ability ability = AbilityRegistry.get("SOUL_SUMMON").orElseThrow();
        assertFalse(ability.getCost().hasCost(),
                "costs are config-only: no cost section means the ability is free");
    }

    @Test
    void healthCostCanBeConfigured() {
        TestConfigProvider provider = new TestConfigProvider();
        TestConfigSection soul = root(provider).create("abilities").create("SOUL_SUMMON");
        soul.set("name", "Soul Summon");
        TestConfigSection cost = soul.create("cost");
        cost.set("mode", "HEALTH");
        cost.set("amount", "5");

        AbilityLoader.loadAll(provider);
        Ability ability = AbilityRegistry.get("SOUL_SUMMON").orElseThrow();
        assertEquals(5, ability.getCost().getCost(CostMode.HEALTH),
                "HEALTH costs must be wired through (SOC/Skill use cases)");
    }

    @Test
    void invalidModeLeavesNoCost() {
        TestConfigProvider provider = new TestConfigProvider();
        TestConfigSection soul = root(provider).create("abilities").create("SOUL_SUMMON");
        soul.set("name", "Soul Summon");
        TestConfigSection cost = soul.create("cost");
        cost.set("mode", "GOLD");
        cost.set("amount", "99");

        AbilityLoader.loadAll(provider);
        Ability ability = AbilityRegistry.get("SOUL_SUMMON").orElseThrow();
        assertFalse(ability.getCost().hasCost(),
                "an invalid mode must be reported and leave the ability without a cost");
    }

    @Test
    void ymlFormulaDefinesDynamicCost() {
        TestConfigProvider provider = new TestConfigProvider();
        TestConfigSection soul = root(provider).create("abilities").create("SOUL_SUMMON");
        soul.set("name", "Soul Summon");
        TestConfigSection cost = soul.create("cost");
        cost.set("mode", "MANA");
        cost.set("formula", "10 + 0.5 * MANA_MAX");

        AbilityLoader.loadAll(provider);
        Ability ability = AbilityRegistry.get("SOUL_SUMMON").orElseThrow();
        CostEntry entry = ability.getCost().getCosts().get(0);
        assertNotNull(entry.formula(), "the yml formula must be carried on the cost entry");
        RPGEntity caster = entity(100);
        assertEquals(60, entry.resolve(caster), "the formula resolves against the caster's stats");
    }

    @Test
    void invalidFormulaLeavesNoCost() {
        TestConfigProvider provider = new TestConfigProvider();
        TestConfigSection soul = root(provider).create("abilities").create("SOUL_SUMMON");
        soul.set("name", "Soul Summon");
        TestConfigSection cost = soul.create("cost");
        cost.set("mode", "MANA");
        cost.set("formula", "10 +");

        AbilityLoader.loadAll(provider);
        Ability ability = AbilityRegistry.get("SOUL_SUMMON").orElseThrow();
        assertFalse(ability.getCost().hasCost(),
                "a syntactically bad formula must be reported and leave the ability without a cost");
    }

    private static RPGEntity entity(double manaMax) {
        StatManager stats = new StatManager(DefaultStats.getDefaultStats());
        stats.addAll(Map.of(StatType.MANA_MAX, new CombatStat("MANA_MAX", manaMax)));
        return new TestRPGEntity(stats);
    }

    private static TestConfigSection root(TestConfigProvider provider) {
        return (TestConfigSection) provider.getRoot();
    }

    private static final class TestRPGEntity extends RPGEntity {
        TestRPGEntity(StatManager stats) {
            super(stats, UUID.randomUUID(), "loader-cost-test", EntityType.MOB, null, null, RPGClassType.NONE);
        }
    }

    // ---------------------------------------------------------------- test config

    private static final class TestConfigProvider implements ConfigProvider {
        private final TestConfigSection root = new TestConfigSection(new HashMap<>());

        @Override
        public ConfigSection getRoot() {
            return root;
        }

        @Override
        public ConfigSection getSection(String path) {
            return root.getSection(path);
        }

        @Override
        public void save() {
        }
    }

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
            // Absent sections are absent (production semantics).
            Object existing = values.get(path);
            return existing instanceof TestConfigSection section ? section : null;
        }

        /**
         * Creates (or returns) a nested section - tests use this to author
         * config, mirroring what a real YAML file would contain.
         */
        public TestConfigSection create(String path) {
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