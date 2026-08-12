package dev.core.item.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.ability.Ability;
import dev.core.ability.AbilityAction;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.storage.AbilityLoader;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/**
 * Replicates DMain.onEnable's exact load order (preregister -> abilities.yml ->
 * items.yml) using in-memory config stubs, then verifies the trigger-match
 * predicate that EquipmentManager.getManualAbilities uses for RIGHT_CLICK.
 */
class ItemAbilityPipelineTest {

    @BeforeEach
    void resetRegistry() {
        AbilityRegistry.clear();
        AbilityRegistry.preregister();
    }

    @Test
    void configuredBoneSwingIsAttachedToItemAndMatchesRightClick() {
        TestConfigProvider provider = new TestConfigProvider();

        // ---- abilities.yml (as committed) ----
        ConfigSection abilitiesRoot = provider.getRoot().getSection("abilities");
        ConfigSection bone = abilitiesRoot.getSection("BONE_SWING");
        bone.set("name", "Swing");
        bone.set("triggerType", "MANUAL");
        bone.set("action", "RIGHT_CLICK");
        bone.set("cooldownScope", "ITEM");
        bone.set("cooldown", 3000);
        ConfigSection particle = abilitiesRoot.getSection("PARTICLE_TEST_ABILITY");
        particle.set("name", "Particle Test");
        particle.set("triggerType", "MANUAL");
        particle.set("action", "RIGHT_CLICK");
        particle.set("cooldownScope", "PLAYER");
        particle.set("cooldown", 0);

        Map<String, Ability> loaded = AbilityLoader.loadAll(provider);
        assertEquals(2, loaded.size(), "both abilities.yml entries must load");
        AbilityRegistry.updateAll(loaded);

        // ---- items.yml (as committed) ----
        ConfigSection itemsRoot = provider.getRoot().getSection("items");
        ConfigSection bonemerang = itemsRoot.getSection("BONEMERANG");
        bonemerang.set("name", "Bonemerang");
        bonemerang.set("material", "BONE");
        bonemerang.set("slot", "MAIN_HAND");
        bonemerang.set("abilities", List.of("BONE_SWING"));

        RPGItem item = RPGItemLoader.load("BONEMERANG", bonemerang);

        assertEquals(1, item.getAbilities().size(), "ability must attach to the item");
        Ability ability = item.getAbilities().get(0);
        assertEquals("BONE_SWING", ability.getId());
        assertEquals(AbilityTriggerType.MANUAL, ability.getTriggerType(),
                "triggerType must come through as MANUAL (set by AbilityLoader)");
        assertEquals(AbilityAction.RIGHT_CLICK, ability.getAction(),
                "action must come through as RIGHT_CLICK (set by AbilityLoader)");

        // the exact predicate used by EquipmentManager.getManualAbilities
        assertTrue(ability.getTriggerType() == AbilityTriggerType.MANUAL
                && ability.getAction() == AbilityAction.RIGHT_CLICK,
                "EquipmentManager would trigger BONE_SWING on a RIGHT_CLICK");
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
    }
}