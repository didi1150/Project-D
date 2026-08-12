package dev.core.item.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.ability.Ability;
import dev.core.ability.AbilityRegistry;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.StatType;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.modifier.StatModifierType;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

class RPGItemLoaderTest {

    private final PrintStream originalOut = System.out;

    @BeforeEach
    void resetRegistry() {
        AbilityRegistry.clear();
        AbilityRegistry.preregister();
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    @Test
    void load_prefersStatModifierTypeCanonicalKey() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection itemSection = provider.getRoot().getSection("items.BONEMERANG");
        itemSection.set("name", "Bonemerang");
        itemSection.set("material", "BONE");
        itemSection.set("slot", "MAIN_HAND");
        itemSection.set("active-stats", List.of(
                Map.of("amount", 20.0, "statType", "ATTACK_DAMAGE", "statModifierType", "PERCENT_ADD")));

        RPGItem item = RPGItemLoader.load("BONEMERANG", itemSection);

        assertEquals(1, item.getActiveStats().size());
        assertEquals(StatModifierType.PERCENT_ADD, item.getActiveStats().get(0).statModifierType);
        assertEquals(20.0, item.getActiveStats().get(0).amount, 0.001);
    }

    @Test
    void load_fallsBackToLegacyModifierTypeKey() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection itemSection = provider.getRoot().getSection("items.BONEMERANG");
        itemSection.set("name", "Bonemerang");
        itemSection.set("material", "BONE");
        itemSection.set("slot", "MAIN_HAND");
        // hand-written config used "modifierType" before the canonical key existed
        itemSection.set("active-stats", List.of(
                Map.of("amount", 10.0, "statType", "CRIT_CHANCE", "modifierType", "PERCENT_ADD")));

        RPGItem item = RPGItemLoader.load("BONEMERANG", itemSection);

        assertEquals(StatModifierType.PERCENT_ADD, item.getActiveStats().get(0).statModifierType);
    }

    @Test
    void load_unknownAbilityIsSkippedWithWarning() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection itemSection = provider.getRoot().getSection("items.BONEMERANG");
        itemSection.set("name", "Bonemerang");
        itemSection.set("material", "BONE");
        itemSection.set("slot", "MAIN_HAND");
        itemSection.set("abilities", List.of("PARTICLE_TEST_ABILITY", "NO_SUCH_ABILITY"));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer));
        RPGItem item = RPGItemLoader.load("BONEMERANG", itemSection);

        // Known ability resolved, unknown one dropped with a warning.
        assertEquals(1, item.getAbilities().size());
        assertEquals("PARTICLE_TEST_ABILITY", item.getAbilities().get(0).getId());
        assertTrue(buffer.toString().contains("NO_SUCH_ABILITY"),
                "expected a warning naming the unknown ability id, got: " + buffer);
    }

    @Test
    void saveItem_writesStatModifierTypeNotStatType() {
        TestConfigProvider provider = new TestConfigProvider();
        RPGItem item = RPGItem.builder("TEST_SWORD", "Test Sword", EquipmentSlot.MAIN_HAND)
                .withMaterial("IRON_SWORD")
                .withActiveStats(List.of(StatModifier.builder(5.0, StatModifierType.PERCENT_ADD,
                        StatType.ATTACK_DAMAGE, "TEST_SWORD").build()))
                .build();

        RPGItemLoader.saveItem(provider, item);

        ConfigSection saved = provider.getRoot().getSection("items").getSection("TEST_SWORD");
        List<ConfigSection> stats = saved.getSectionList("active-stats");
        assertEquals(1, stats.size());
        assertEquals("PERCENT_ADD", stats.get(0).getString("statModifierType", "FLAT"));
        // Bug 5 regression: must not write the stat type name into the modifier type slot.
        assertEquals("PERCENT_ADD", stats.get(0).getString("statModifierType", null));
    }

    @Test
    void registry_acceptsCodeRegisteredAbilities() {
        AbilityRegistry.register(new TestAbility("TEST_ABILITY"));

        assertTrue(AbilityRegistry.get("TEST_ABILITY").isPresent());
    }

    /** Minimal ability impl for registry test. */
    private static final class TestAbility extends Ability {
        TestAbility(String id) {
            super(id);
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
            Object value = resolve(path);
            if (value instanceof List<?> list) {
                List<String> result = new ArrayList<>();
                for (Object item : list) {
                    result.add(String.valueOf(item));
                }
                return result;
            }
            return new ArrayList<>();
        }

        @Override
        public ConfigSection getSection(String path) {
            Object value = resolve(path);
            if (value instanceof TestConfigSection section) {
                return section;
            }
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                return new TestConfigSection(typed);
            }
            TestConfigSection section = new TestConfigSection(new HashMap<>());
            set(path, section);
            return section;
        }

        @Override
        public List<ConfigSection> getSectionList(String path) {
            Object value = resolve(path);
            if (value instanceof List<?> list) {
                List<ConfigSection> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        result.add(new TestConfigSection(new HashMap<>(toStringMap(map))));
                    }
                }
                return result;
            }
            return new ArrayList<>();
        }

        @Override
        public void set(String path, Object value) {
            if (path == null || path.isBlank()) {
                return;
            }
            String[] parts = path.split("\\.");
            Map<String, Object> target = values;
            for (int i = 0; i < parts.length - 1; i++) {
                String key = parts[i];
                Object nested = target.get(key);
                if (!(nested instanceof Map<?, ?> map)) {
                    nested = new HashMap<String, Object>();
                    target.put(key, nested);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) nested;
                target = typed;
            }
            target.put(parts[parts.length - 1], value);
        }

        @Override
        public Set<String> getKeys() {
            return values.keySet();
        }

        private Object resolve(String path) {
            if (path == null || path.isBlank()) {
                return null;
            }
            String[] parts = path.split("\\.");
            Object value = values;
            for (String part : parts) {
                if (value instanceof Map<?, ?> map) {
                    value = map.get(part);
                } else {
                    return null;
                }
            }
            return value;
        }

        private static Map<String, Object> toStringMap(Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
    }
}
