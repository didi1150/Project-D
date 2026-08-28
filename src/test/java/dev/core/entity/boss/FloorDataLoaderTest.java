package dev.core.entity.boss;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

class FloorDataLoaderTest {

    @Test
    void loadAll_parsesFloorDataWithViewPoints() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection root = provider.getRoot();
        root.set("floor-data.1.wipe-height", 100.0);
        root.set("floor-data.1.suppress-height", 25.0);
        root.set("floor-data.1.pillars", List.of(Map.of("x", 10, "y", 64, "z", 10), Map.of("x", -10, "y", 64, "z", 10),
                Map.of("x", 10, "y", 64, "z", -10), Map.of("x", -10, "y", 64, "z", -10)));
        root.set("floor-data.1.single-point",
                Map.of("x", 1, "y", 2, "z", 3, "world", "world_nether", "yaw", 90.0, "pitch", 45.0));
        root.set("bosses.1.wither-king.entity-type", "WITHER");

        Map<Integer, FloorData> map = FloorDataLoader.loadAll(provider);
        assertEquals(1, map.size());
        FloorData fd = map.get(1);
        assertNotNull(fd);
        assertEquals(100.0, fd.getDouble("wipe-height", 0), 0.001);
        assertEquals(25.0, fd.getDouble("suppress-height", 0), 0.001);
        var pillars = fd.getViewPointList("pillars");
        assertEquals(4, pillars.size());
        assertEquals(10, pillars.get(0).getX());
        assertEquals(64, pillars.get(0).getY());
        var vp = fd.getViewPoint("single-point");
        assertNotNull(vp);
        assertEquals("world_nether", vp.getWorld());
        assertEquals(90f, vp.getYaw(), 0.001);
        assertEquals(45f, vp.getPitch(), 0.001);
    }

    @Test
    void loadAll_ignoresNonNumericAndEmpty() {
        TestConfigProvider provider = new TestConfigProvider();
        provider.getRoot().set("floor-data.one.wipe-height", 10);
        provider.getRoot().set("floor-data.1.a", 1);
        Map<Integer, FloorData> map = FloorDataLoader.loadAll(provider);
        assertEquals(1, map.size());
        assertTrue(map.containsKey(1));
        // empty floor-data should yield empty map
        TestConfigProvider empty = new TestConfigProvider();
        assertTrue(FloorDataLoader.loadAll(empty).isEmpty());
    }

    @Test
    void floorData_helpers_emptyWhenMissing() {
        FloorData empty = FloorData.empty(5);
        assertTrue(empty.isEmpty());
        assertTrue(empty.getViewPointList("pillars").isEmpty());
        assertNull(empty.getViewPoint("missing"));
        assertEquals(0, empty.getDouble("x", 0), 0.001);
    }

    @Test
    void registry_snapshotAndReload() {
        FloorDataRegistry reg = FloorDataRegistry.getInstance();
        reg.clear();
        TestConfigProvider p = new TestConfigProvider();
        p.getRoot().set("floor-data.1.x", 1);
        p.getRoot().set("floor-data.2.x", 2);
        reg.registerAll(FloorDataLoader.loadAll(p));
        assertEquals(2, reg.size());
        assertTrue(reg.get(1).isPresent());
        assertEquals(99, reg.getOrEmpty(99).getFloor());
        assertTrue(reg.getOrEmpty(99).isEmpty());
        reg.clear();
        assertEquals(0, reg.size());
    }

    // Reuse TestConfigProvider from BossDefinitionLoaderTest pattern
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

        TestConfigSection(Map<String, Object> values) {
            this.values = values;
        }

        @Override
        public String getString(String path, String def) {
            Object v = resolve(path);
            return v == null ? def : v.toString();
        }

        @Override
        public int getInt(String path, int def) {
            Object v = resolve(path);
            return v == null ? def : Integer.parseInt(v.toString());
        }

        @Override
        public double getDouble(String path, double def) {
            Object v = resolve(path);
            return v == null ? def : Double.parseDouble(v.toString());
        }

        @Override
        public boolean getBoolean(String path, boolean def) {
            Object v = resolve(path);
            return v == null ? def : Boolean.parseBoolean(v.toString());
        }

        @Override
        public List<String> getStringList(String path) {
            Object v = resolve(path);
            if (v instanceof List<?> l) {
                List<String> r = new ArrayList<>();
                for (Object o : l)
                    r.add(String.valueOf(o));
                return r;
            }
            return new ArrayList<>();
        }

        @Override
        public ConfigSection getSection(String path) {
            Object v = resolve(path);
            if (v instanceof TestConfigSection s)
                return s;
            if (v instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> t = (Map<String, Object>) m;
                return new TestConfigSection(t);
            }
            TestConfigSection sec = new TestConfigSection(new HashMap<>());
            set(path, sec);
            return sec;
        }

        @Override
        public List<ConfigSection> getSectionList(String path) {
            Object v = resolve(path);
            if (v instanceof List<?> l) {
                List<ConfigSection> r = new ArrayList<>();
                for (Object o : l) {
                    if (o instanceof Map<?, ?> m)
                        r.add(new TestConfigSection(new HashMap<>(toStringMap(m))));
                }
                return r;
            }
            return new ArrayList<>();
        }

        @Override
        public void set(String path, Object value) {
            if (path == null || path.isBlank())
                return;
            String[] parts = path.split("\\.");
            Map<String, Object> target = values;
            for (int i = 0; i < parts.length - 1; i++) {
                String k = parts[i];
                Object n = target.get(k);
                if (!(n instanceof Map<?, ?> m)) {
                    n = new HashMap<String, Object>();
                    target.put(k, n);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> t = (Map<String, Object>) n;
                target = t;
            }
            target.put(parts[parts.length - 1], value);
        }

        @Override
        public Set<String> getKeys() {
            return values.keySet();
        }

        private Object resolve(String path) {
            if (path == null || path.isBlank())
                return null;
            String[] parts = path.split("\\.");
            Object v = values;
            for (String p : parts) {
                if (v instanceof Map<?, ?> m)
                    v = m.get(p);
                else
                    return null;
            }
            return v;
        }

        private static Map<String, Object> toStringMap(Map<?, ?> m) {
            Map<String, Object> r = new HashMap<>();
            for (var e : m.entrySet())
                r.put(String.valueOf(e.getKey()), e.getValue());
            return r;
        }
    }
}
