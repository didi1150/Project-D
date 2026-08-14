package dev.core.item.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void item_armorClassificationMatchesArmorSlots() {
        assertTrue(RPGItem.builder("H", "Head", EquipmentSlot.HEAD).build().isArmor());
        assertTrue(RPGItem.builder("C", "Chest", EquipmentSlot.CHEST).build().isArmor());
        assertTrue(RPGItem.builder("L", "Legs", EquipmentSlot.LEGS).build().isArmor());
        assertTrue(RPGItem.builder("F", "Feet", EquipmentSlot.FEET).build().isArmor());
        assertFalse(RPGItem.builder("W", "Main", EquipmentSlot.MAIN_HAND).build().isArmor());
        assertFalse(RPGItem.builder("O", "Off", EquipmentSlot.OFF_HAND).build().isArmor());
    }

    @Test
    void item_levelGateUsesUnlockLevel() {
        RPGItem gated = RPGItem.builder("GATED", "Gated", EquipmentSlot.MAIN_HAND).withUnlockLevel(5).build();

        assertFalse(gated.isUsableAtLevel(0));
        assertFalse(gated.isUsableAtLevel(4));
        assertTrue(gated.isUsableAtLevel(5));
        assertTrue(gated.isUsableAtLevel(10));
    }

    @Test
    void item_levelGateDefaultsToUnlocked() {
        RPGItem ungated = RPGItem.builder("FREE", "Free", EquipmentSlot.MAIN_HAND).build();

        assertTrue(ungated.isUsableAtLevel(0));
        assertTrue(ungated.isUsableAtLevel(1));
    }

    @Test
    void load_readsUnlockLevelFromConfig() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection itemSection = provider.getRoot().getSection("items.GATED_SWORD");
        itemSection.set("name", "Gated Sword");
        itemSection.set("material", "IRON_SWORD");
        itemSection.set("slot", "MAIN_HAND");
        itemSection.set("unlockLevel", 7);

        RPGItem item = RPGItemLoader.load("GATED_SWORD", itemSection);

        assertEquals(7, item.getUnlockLevel());
        assertTrue(item.isUsableAtLevel(7));
        assertFalse(item.isUsableAtLevel(6));
    }

    @Test
    void load_readsLeatherColorHexAndSkullCustomization() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection itemSection = provider.getRoot().getSection("items.HELMET");
        itemSection.set("name", "Leather Helmet");
        itemSection.set("material", "LEATHER_HELMET");
        itemSection.set("slot", "HEAD");
        itemSection.set("leather-color", "#FF8000");
        itemSection.set("skull-owner", "Notch");

        RPGItem item = RPGItemLoader.load("HELMET", itemSection);

        assertEquals(0xFF8000, item.getLeatherColor().get().intValue());
        assertEquals("Notch", item.getSkullOwner().get());
        assertTrue(item.getSkullTexture().isEmpty());
    }

    @Test
    void load_readsLeatherColorDecimal() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection itemSection = provider.getRoot().getSection("items.HELMET");
        itemSection.set("name", "Leather Helmet");
        itemSection.set("material", "LEATHER_HELMET");
        itemSection.set("slot", "HEAD");
        itemSection.set("leather-color", 16711680);

        RPGItem item = RPGItemLoader.load("HELMET", itemSection);

        assertEquals(0xFF0000, item.getLeatherColor().get().intValue());
    }

    @Test
    void load_ignoresInvalidLeatherColor() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection itemSection = provider.getRoot().getSection("items.HELMET");
        itemSection.set("name", "Leather Helmet");
        itemSection.set("material", "LEATHER_HELMET");
        itemSection.set("slot", "HEAD");
        itemSection.set("leather-color", "not-a-color");

        RPGItem item = RPGItemLoader.load("HELMET", itemSection);

        assertTrue(item.getLeatherColor().isEmpty());
    }

    @Test
    void load_readsSkullTexture() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection itemSection = provider.getRoot().getSection("items.SKULL");
        itemSection.set("name", "Custom Skull");
        itemSection.set("material", "PLAYER_HEAD");
        itemSection.set("slot", "HEAD");
        itemSection.set("skull-texture", "eyJ0ZXh0dXJlcyI6e319");

        RPGItem item = RPGItemLoader.load("SKULL", itemSection);

        assertEquals("eyJ0ZXh0dXJlcyI6e319", item.getSkullTexture().get());
    }

    @Test
    void saveItem_writesVisualCustomization() {
        TestConfigProvider provider = new TestConfigProvider();
        RPGItem item = RPGItem.builder("SKULL", "Custom Skull", "PLAYER_HEAD", EquipmentSlot.HEAD)
                .withLeatherColor(0x8B0000)
                .withSkullOwner("Notch")
                .withSkullTexture("eyJ0ZXh0dXJlcyI6e319")
                .build();

        RPGItemLoader.saveItem(provider, item);

        ConfigSection saved = provider.getRoot().getSection("items").getSection("SKULL");
        assertEquals(0x8B0000, saved.getInt("leather-color", -1));
        assertEquals("Notch", saved.getString("skull-owner", null));
        assertEquals("eyJ0ZXh0dXJlcyI6e319", saved.getString("skull-texture", null));
    }

    @Test
    void deserialize_roundTripsVisualCustomization() {
        RPGItem original = RPGItem.builder("HELMET", "Leather Helmet", "LEATHER_HELMET", EquipmentSlot.HEAD)
                .withLeatherColor(0x3366FF)
                .withSkullTexture("eyJ0ZXh0dXJlcyI6e319")
                .build();

        RPGItem restored = RPGItem.deserialize("HELMET", original.serialize());

        assertEquals(original.getLeatherColor(), restored.getLeatherColor());
        assertEquals(original.getSkullOwner(), restored.getSkullOwner());
        assertEquals(original.getSkullTexture(), restored.getSkullTexture());
    }

    @Test
    void parseRgbColor_acceptsHexDecimalAndNumber() {
        assertEquals(0x3366FF, RPGItem.parseRgbColor("#3366FF"));
        assertEquals(0x3366FF, RPGItem.parseRgbColor("0x3366FF"));
        assertEquals(3368703, RPGItem.parseRgbColor("3368703"));
        assertEquals(0xFF0000, RPGItem.parseRgbColor(16711680));
        assertEquals(null, RPGItem.parseRgbColor("garbage"));
        assertEquals(null, RPGItem.parseRgbColor(null));
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
