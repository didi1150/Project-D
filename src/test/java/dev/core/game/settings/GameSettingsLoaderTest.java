package dev.core.game.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.core.game.coords.Point3D;
import dev.core.game.coords.ViewPoint3D;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

class GameSettingsLoaderTest {

    @Test
    void load_setsBossWorldFromConfig() {
        TestConfigProvider provider = new TestConfigProvider();
        provider.getRoot().set("bossworld", "boss-arena");

        GameSettings settings = GameSettings.getCurrentSettings();
        settings.setBossWorld(null);

        new GameSettingsLoader(settings, provider).load();

        assertEquals("boss-arena", settings.getBossWorld());
    }

    @Test
    void load_readsFloorRewardLocations() {
        TestConfigProvider provider = new TestConfigProvider();
        provider.getRoot().set("locations.rewardplayerspawn.2.x", 10);
        provider.getRoot().set("locations.rewardplayerspawn.2.y", 65);
        provider.getRoot().set("locations.rewardplayerspawn.2.z", 20);
        provider.getRoot().set("locations.rewardplayerspawn.2.world", "boss");
        provider.getRoot().set("locations.rewardchest.2.x", 4);
        provider.getRoot().set("locations.rewardchest.2.y", 64);
        provider.getRoot().set("locations.rewardchest.2.z", 6);
        provider.getRoot().set("locations.rewardchest.2.world", "boss");

        GameSettings settings = GameSettings.getCurrentSettings();
        new GameSettingsLoader(settings, provider).load();

        assertEquals(10, settings.getRewardPlayerSpawnLocation(2).getX());
        assertEquals(20, settings.getRewardPlayerSpawnLocation(2).getZ());
        assertEquals(4, settings.getRewardChestLocation(2).getX());
        assertEquals(6, settings.getRewardChestLocation(2).getZ());
        assertNull(settings.getRewardPlayerSpawnLocation(2).getWorld());
        assertNull(settings.getRewardChestLocation(2).getWorld());
        assertNull(provider.getSection("locations.rewardplayerspawn.2").getString("world", null));
        assertNull(provider.getSection("locations.rewardchest.2").getString("world", null));
    }

    @Test
    void setRewardLocations_omitsWorldField() {
        TestConfigProvider provider = new TestConfigProvider();
        GameSettings settings = GameSettings.getCurrentSettings();
        GameSettingsLoader loader = new GameSettingsLoader(settings, provider);

        loader.setRewardPlayerSpawnLocation(2, new ViewPoint3D(10, 65, 20, "boss", 90f, 45f));
        loader.setRewardChestLocation(2, new Point3D(4, 64, 6, "boss"));

        assertNull(settings.getRewardPlayerSpawnLocation(2).getWorld());
        assertNull(settings.getRewardChestLocation(2).getWorld());
        assertNull(provider.getSection("locations.rewardplayerspawn.2").getString("world", null));
        assertNull(provider.getSection("locations.rewardchest.2").getString("world", null));
    }

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
        @SuppressWarnings("unchecked")
        public ConfigSection getSection(String path) {
            Object value = resolve(path);
            if (value instanceof TestConfigSection section) {
                return section;
            }
            if (value instanceof Map<?, ?> map) {
                return new TestConfigSection((Map<String, Object>) map);
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
