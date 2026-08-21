package dev.bukkit.item.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

class LoreLabelsTest {

    @AfterEach
    void restoreDefaults() {
        LoreLabels.reset();
    }

    @Test
    void format_usesBuiltInDefaultsWithoutLoad() {
        String levelLine = LoreLabels.format(LoreLabels.LEVEL_LINE, "level", 5, "type", "SWORD");
        assertEquals(ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + "LEVEL 5: SWORD ITEM", levelLine);

        String abilityLabel = LoreLabels.format(LoreLabels.ABILITY_LABEL, "name", "Fireball");
        assertEquals(ChatColor.YELLOW.toString() + "Ability: Fireball", abilityLabel);
    }

    @Test
    void load_overridesOnlyTheProvidedKeys() {
        Map<String, Object> lore = new HashMap<>();
        lore.put("level-line", "<red/>TIER {level} {type}");
        LoreLabels.load(provider(lore));

        assertEquals(ChatColor.RED.toString() + "TIER 7 HELMET",
                LoreLabels.format(LoreLabels.LEVEL_LINE, "level", 7, "type", "HELMET"));
        // Untouched keys keep their defaults.
        assertEquals(ChatColor.DARK_GRAY.toString() + "Cost:",
                LoreLabels.format(LoreLabels.COST_LABEL));
    }

    @Test
    void load_withMissingKeysFallsBackToDefaults() {
        Map<String, Object> lore = new HashMap<>();
        lore.put("passive-stats-header", "<blue/>Base Stats:");
        LoreLabels.load(provider(lore));

        assertEquals(ChatColor.BLUE.toString() + "Base Stats:",
                LoreLabels.format(LoreLabels.PASSIVE_STATS_HEADER));
        // Keys absent from the config keep their defaults.
        assertEquals(ChatColor.GOLD.toString() + "Item Abilities:",
                LoreLabels.format(LoreLabels.ABILITIES_HEADER));
    }

    @Test
    void load_withEmptyLoreSectionKeepsDefaults() {
        LoreLabels.load(provider(new HashMap<>()));

        assertEquals(ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + "LEVEL 2: BOW ITEM",
                LoreLabels.format(LoreLabels.LEVEL_LINE, "level", 2, "type", "BOW"));
    }

    @Test
    void format_leavesUnknownPlaceholdersUntouched() {
        String result = LoreLabels.format(LoreLabels.ABILITY_LABEL, "name", "Blink {unknown}");
        assertEquals(ChatColor.YELLOW.toString() + "Ability: Blink {unknown}", result);
    }

    // ------------------------------------------------------------------
    // Minimal map-backed config stubs (same pattern as the loader tests)
    // ------------------------------------------------------------------

    private static ConfigProvider provider(Map<String, Object> loreValues) {
        Map<String, Object> root = new HashMap<>();
        root.put("lore", new HashMap<>(loreValues));
        return new TestConfigProvider(root);
    }

    private static final class TestConfigProvider implements ConfigProvider {
        private final TestConfigSection root;

        TestConfigProvider(Map<String, Object> values) {
            this.root = new TestConfigSection(values);
        }

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

        TestConfigSection(Map<String, Object> values) {
            this.values = values;
        }

        @Override
        public String getString(String path, String def) {
            Object value = values.get(path);
            return value == null ? def : value.toString();
        }

        @Override
        public int getInt(String path, int def) {
            Object value = values.get(path);
            return value == null ? def : Integer.parseInt(value.toString());
        }

        @Override
        public double getDouble(String path, double def) {
            Object value = values.get(path);
            return value == null ? def : Double.parseDouble(value.toString());
        }

        @Override
        public boolean getBoolean(String path, boolean def) {
            Object value = values.get(path);
            return value == null ? def : Boolean.parseBoolean(value.toString());
        }

        @Override
        public List<String> getStringList(String path) {
            return new ArrayList<>();
        }

        @Override
        public ConfigSection getSection(String path) {
            Object value = values.get(path);
            if (value instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> child = (Map<String, Object>) value;
                return new TestConfigSection(child);
            }
            return new TestConfigSection(new HashMap<>());
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
