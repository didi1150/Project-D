package dev.core.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.item.display.TextColor;
import dev.core.stat.adapter.StatTypeAdapter;
import dev.core.stat.descriptor.StatColor;
import dev.core.stat.descriptor.StatDescriptor;
import dev.core.stat.descriptor.StatRegistry;
import dev.core.stat.loader.StatMetadataLoader;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/**
 * Covers the data-driven stat metadata: descriptor seeding from StatType,
 * percent semantics (CRIT_CHANCE / ATTACK_SPEED), StatColor parsing, registry
 * override, and the stats.yml {@code statMetadata} section loader.
 */
class StatMetadataTest {

    @BeforeEach
    void setUp() {
        StatRegistry.getInstance().clear();
        StatTypeAdapter.initializeStatTypes();
    }

    @Test
    void critChanceUsesPercentSemantics() {
        StatDescriptor crit = StatRegistry.getInstance().get("core:crit_chance").orElseThrow();
        assertTrue(crit.isPercent(), "CRIT_CHANCE must be percent-flagged");
        // 0.25 as a fraction -> "25.0%" (legacy StatType printed the raw fraction)
        assertEquals("⚹ +25.0% Critical Chance", StatType.CRIT_CHANCE.formatValue(0.25, true));
        assertEquals(StatType.CRIT_CHANCE.formatValue(0.25, true), crit.formatValue(0.25, true),
                "StatType must delegate to the descriptor");
    }

    @Test
    void attackSpeedUsesPercentSemantics() {
        assertTrue(StatRegistry.getInstance().get("core:attack_speed").orElseThrow().isPercent());
        assertEquals("⚡ +150.0% Attack Speed", StatType.ATTACK_SPEED.formatValue(1.5, true));
    }

    @Test
    void integerStatsFormatWithoutDecimals() {
        assertEquals("⚔ +15 Attack Damage", StatType.ATTACK_DAMAGE.formatValue(15, true));
        assertEquals("⚔ 15 Attack Damage", StatType.ATTACK_DAMAGE.formatValue(15, false));
        assertEquals("⚔ -15 Attack Damage", StatType.ATTACK_DAMAGE.formatValue(-15, true));
    }

    @Test
    void formatColoredValueHasNoColorNamePrefix() {
        StatDescriptor desc = StatRegistry.getInstance().get("core:attack_damage").orElseThrow();
        assertEquals(desc.formatValue(15.0, true), desc.formatColoredValue(15.0, true));
    }

    @Test
    void overrideReplacesDescriptor() {
        StatRegistry registry = StatRegistry.getInstance();
        registry.override(new StatDescriptor("core:attack_damage", "Dmg", "×", StatColor.of("#FF0000"),
                StatDescriptor.StatCategory.ATTRIBUTE, false));

        StatDescriptor replaced = registry.get("core:attack_damage").orElseThrow();
        assertEquals("Dmg", replaced.getDisplayName());
        assertEquals("×", replaced.getSymbol());
        assertEquals("#FF0000", replaced.getColor().toHex());
        assertEquals("× +15 Dmg", replaced.formatValue(15, true));
        assertEquals(19, registry.size(), "override must not grow the registry");
    }

    @Test
    void registerThrowsOnDuplicateId() {
        StatRegistry registry = StatRegistry.getInstance();
        StatDescriptor existing = registry.get("core:armor").orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> registry.register(existing));
    }

    @Test
    void statColorParsing() {
        assertEquals(TextColor.GOLD, StatColor.fromString("GOLD").getNamed());
        assertFalse(StatColor.fromString("GOLD").isCustom());

        StatColor hex = StatColor.fromString("#ffd700");
        assertTrue(hex.isCustom());
        assertEquals("#FFD700", hex.getHex());

        assertEquals("#FFD700", StatColor.fromString("0xffd700").getHex());

        assertNull(StatColor.fromString("notacolor"));
        assertNull(StatColor.fromString("#12345"));
        assertNull(StatColor.fromString(""));
        assertNull(StatColor.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> StatColor.of("#GGGGGG"));
    }

    @Test
    void metadataLoaderAppliesOverrides() {
        StatMetadataLoader.loadStatMetadata(providerWith(meta -> {
            TestConfigSection attack = meta.create("core:attack_damage");
            attack.set("display-name", "Damage");
            attack.set("symbol", "†");
            attack.set("color", "#00FF00");
        }));

        StatDescriptor attack = StatRegistry.getInstance().get("core:attack_damage").orElseThrow();
        assertEquals("Damage", attack.getDisplayName());
        assertEquals("†", attack.getSymbol());
        assertEquals("#00FF00", attack.getColor().toHex());
        assertEquals("† +15 Damage", attack.formatValue(15, true));
    }

    @Test
    void metadataLoaderShortNameAccepted() {
        StatMetadataLoader.loadStatMetadata(providerWith(meta -> {
            TestConfigSection haste = meta.create("ability_haste");
            haste.set("color", "RED");
            haste.set("display-name", "AH");
        }));

        StatDescriptor haste = StatRegistry.getInstance().get("core:ability_haste").orElseThrow();
        assertEquals("AH", haste.getDisplayName());
        assertEquals(TextColor.RED, haste.getColor().getNamed());
    }

    @Test
    void metadataLoaderPercentOverride() {
        // HEAL_AND_SHIELD_POWER is not percent by default; flag it via config.
        StatMetadataLoader.loadStatMetadata(providerWith(meta -> {
            TestConfigSection heal = meta.create("core:heal_and_shield_power");
            heal.set("percent", true);
        }));

        StatDescriptor heal = StatRegistry.getInstance().get("core:heal_and_shield_power").orElseThrow();
        assertTrue(heal.isPercent());
        assertEquals("♥ +10.0% Heal & Shield Power", heal.formatValue(0.10, true));
    }

    @Test
    void metadataLoaderUnknownIdSkipped() {
        StatMetadataLoader.loadStatMetadata(providerWith(meta -> {
            meta.create("core:unknown_stat").set("display-name", "X");
        }));

        assertFalse(StatRegistry.getInstance().isRegistered("core:unknown_stat"));
        assertTrue(StatRegistry.getInstance().isRegistered("core:armor"),
                "existing stats must be untouched");
    }

    @Test
    void metadataLoaderInvalidColorKeepsExisting() {
        StatMetadataLoader.loadStatMetadata(providerWith(meta -> {
            meta.create("core:armor").set("color", "#nope");
        }));

        StatDescriptor armor = StatRegistry.getInstance().get("core:armor").orElseThrow();
        assertEquals(TextColor.GRAY, armor.getColor().getNamed(), "invalid color must fall back");
    }

    private interface MetaSetter {
        void apply(TestConfigSection meta);
    }

    private static ConfigProvider providerWith(MetaSetter setter) {
        TestConfigProvider provider = new TestConfigProvider();
        TestConfigSection root = (TestConfigSection) provider.getRoot();
        TestConfigSection meta = root.create("statMetadata");
        setter.apply(meta);
        return provider;
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
            String head = path.substring(0, path.indexOf('.'));
            Object next = values.get(head);
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