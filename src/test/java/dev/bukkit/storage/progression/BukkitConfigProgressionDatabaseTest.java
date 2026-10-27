package dev.bukkit.storage.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.progression.PlayerClassProgression;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/**
 * Exercises the persistence paths used by the YAML-backed progression adapter
 * through the same ConfigProvider abstraction the real Bukkit provider uses.
 */
class BukkitConfigProgressionDatabaseTest {

    @Test
    void classProgressionsActiveClassAndUnlocksSurviveProviderRoundTrip() {
        MapConfigProvider provider = new MapConfigProvider();
        UUID playerId = UUID.randomUUID();
        PlayerClassProgression mage = new PlayerClassProgression(RPGClassType.MAGE);
        mage.addXp(350);

        BukkitConfigProgressionDatabase database = new BukkitConfigProgressionDatabase(provider);
        database.save(playerId, mage);
        database.setActiveClass(playerId, RPGClassType.MAGE);
        database.saveUnlockedItems(playerId, Set.of("SEALBREAKER", "SEALED_SCEPTRE"));

        BukkitConfigProgressionDatabase reloaded = new BukkitConfigProgressionDatabase(provider);
        Map<RPGClassType, PlayerClassProgression> loaded = reloaded.loadAll(playerId);
        PlayerClassProgression loadedMage = loaded.get(RPGClassType.MAGE);

        assertTrue(loadedMage != null);
        assertEquals(mage.getXp(), loadedMage.getXp());
        assertEquals(mage.getLevel(), loadedMage.getLevel());
        assertEquals(mage.getUsableItems(), loadedMage.getUsableItems());
        assertEquals(RPGClassType.MAGE, reloaded.getActiveClass(playerId));
        assertEquals(Set.of("SEALBREAKER", "SEALED_SCEPTRE"), reloaded.loadUnlockedItems(playerId));
    }

    private static final class MapConfigProvider implements ConfigProvider {
        private final MapSection root = new MapSection(new HashMap<>());

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
            // The real provider writes to disk; the in-memory tree is the source
            // of truth for this adapter-path test.
        }
    }

    private static final class MapSection implements ConfigSection {
        private final Map<String, Object> values;

        private MapSection(Map<String, Object> values) {
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
            return value instanceof Number number ? number.intValue() : def;
        }

        @Override
        public double getDouble(String path, double def) {
            Object value = resolve(path);
            return value instanceof Number number ? number.doubleValue() : def;
        }

        @Override
        public boolean getBoolean(String path, boolean def) {
            Object value = resolve(path);
            return value instanceof Boolean bool ? bool : def;
        }

        @Override
        public List<String> getStringList(String path) {
            Object value = resolve(path);
            if (!(value instanceof List<?> list)) {
                return Collections.emptyList();
            }
            List<String> out = new ArrayList<>();
            for (Object entry : list) {
                out.add(String.valueOf(entry));
            }
            return out;
        }

        @Override
        public ConfigSection getSection(String path) {
            Object value = resolve(path);
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                return new MapSection(typed);
            }
            Map<String, Object> created = new HashMap<>();
            set(path, created);
            return new MapSection(created);
        }

        @Override
        public List<ConfigSection> getSectionList(String path) {
            return Collections.emptyList();
        }

        @Override
        public void set(String path, Object value) {
            String[] parts = path.split("\\.");
            Map<String, Object> target = values;
            for (int i = 0; i < parts.length - 1; i++) {
                Object nested = target.get(parts[i]);
                Map<String, Object> child;
                if (nested instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) nested;
                    child = typed;
                } else {
                    child = new HashMap<>();
                    target.put(parts[i], child);
                }
                target = child;
            }
            target.put(parts[parts.length - 1], value);
        }

        @Override
        public Set<String> getKeys() {
            return values.keySet();
        }

        private Object resolve(String path) {
            Object value = values;
            for (String part : path.split("\\.")) {
                if (!(value instanceof Map<?, ?> map)) {
                    return null;
                }
                value = map.get(part);
            }
            return value;
        }
    }
}
