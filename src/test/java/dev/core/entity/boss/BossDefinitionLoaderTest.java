package dev.core.entity.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.core.stat.StatType;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

class BossDefinitionLoaderTest {

    @Test
    void loadAll_parsesFloorBossStatsAndStages() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection root = provider.getRoot();
        root.set("bosses.1.wither-king.display-name", "&cWither King");
        root.set("bosses.1.wither-king.entity-type", "WITHER");
        root.set("bosses.1.wither-king.defeat-stage", "death");
        root.set("bosses.1.wither-king.stats.HEALTH_MAX", 15000);
        root.set("bosses.1.wither-king.stats.ATTACK_DAMAGE", 80);
        root.set("bosses.1.wither-king.stages", List.of(
                Map.of("type", "THRESHOLD", "id", "phase1", "health-threshold", 0.75, "next-stage", "phase2"),
                Map.of("type", "MONOLOGUE", "id", "death", "lines", List.of("I... cannot... fall..."))));

        List<BossDefinition> definitions = BossDefinitionLoader.loadAll(provider,
                new TestStageTypeRegistry(), new TestStrategyRegistry());

        assertEquals(1, definitions.size());
        BossDefinition definition = definitions.get(0);
        assertEquals("wither-king", definition.getId());
        assertEquals("&cWither King", definition.getDisplayName());
        assertEquals("WITHER", definition.getEntityType());
        assertEquals(1, definition.getFloor());
        assertEquals("death", definition.getDefeatStageId());
        assertEquals(15000, definition.getBaseStatManager().getCurrentValue(StatType.HEALTH_MAX,
                System.currentTimeMillis()));
        assertEquals(80, definition.getBaseStatManager().getCurrentValue(StatType.ATTACK_DAMAGE,
                System.currentTimeMillis()));

        assertEquals(2, definition.getStages().size());
        assertEquals("phase1", definition.getStages().get(0).getId());
        assertEquals("death", definition.getStages().get(1).getId());
        assertTrue(definition.getStages().get(0) instanceof HealthThresholdBossStage);
        assertTrue(definition.getStages().get(1) instanceof MonologueBossStage);
    }

    @Test
    void loadAll_ignoresNonNumericFloorKeys() {
        TestConfigProvider provider = new TestConfigProvider();
        provider.getRoot().set("bosses.one.wither-king.display-name", "Broken");
        provider.getRoot().set("bosses.1.wither-king.display-name", "Good");
        provider.getRoot().set("bosses.1.wither-king.entity-type", "WITHER");

        List<BossDefinition> definitions = BossDefinitionLoader.loadAll(provider,
                new TestStageTypeRegistry(), new TestStrategyRegistry());

        assertEquals(1, definitions.size());
        assertEquals("Good", definitions.get(0).getDisplayName());
    }

    @Test
    void registry_returnsFirstDefinitionPerFloor() {
        BossDefinitionRegistry registry = BossDefinitionRegistry.getInstance();
        registry.clear();

        TestConfigProvider provider = new TestConfigProvider();
        provider.getRoot().set("bosses.1.wither-king.entity-type", "WITHER");
        provider.getRoot().set("bosses.1.skeleton-lord.entity-type", "SKELETON");
        provider.getRoot().set("bosses.2.dragon.entity-type", "ENDER_DRAGON");

        registry.registerAll(BossDefinitionLoader.loadAll(provider, new TestStageTypeRegistry(),
                new TestStrategyRegistry()));

        Optional<BossDefinition> floor1 = registry.getForFloor(1);
        assertTrue(floor1.isPresent());
        assertEquals("wither-king", floor1.get().getId());
        assertTrue(registry.getForFloor(2).isPresent());
        assertEquals("dragon", registry.getForFloor(2).get().getId());
        assertFalse(registry.getForFloor(3).isPresent());
    }

    private static final class TestStageTypeRegistry implements BossStageTypeRegistry {
        private final Map<String, BossStageType> types = Map.of("THRESHOLD", new ThresholdStageType(), "MONOLOGUE",
                new MonologueStageType());

        @Override
        public Optional<BossStageType> resolve(String typeKey) {
            return Optional.ofNullable(types.get(typeKey.toUpperCase()));
        }
    }

    private static final class TestStrategyRegistry implements BossStrategyRegistry {
        @Override
        public Optional<MovementStrategy> movement(String key, ConfigSection params) {
            return "NONE".equalsIgnoreCase(key) ? Optional.of(new NoopMovementStrategy()) : Optional.empty();
        }

        @Override
        public Optional<TargetingStrategy> targeting(String key, ConfigSection params) {
            return Optional.empty();
        }

        @Override
        public Optional<AttackPattern> attack(String key, ConfigSection params) {
            return Optional.empty();
        }
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
