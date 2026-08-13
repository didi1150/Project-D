package dev.core.entity.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.game.dungeon.proceduralDungeon.util.SpawnTier;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

class MobDefinitionLoaderTest {

    @BeforeEach
    void resetRegistry() {
        MobDefinitionRegistry.getInstance().clear();
    }

    @Test
    void loadAll_parsesMobDefinitionFields() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection root = provider.getRoot().getSection("dungeon-mobs");
        ConfigSection def = root.getSection("bonemerang-zombie");
        def.set("entity-type", "ZOMBIE");
        def.set("weight", 4);
        def.set("tiers", List.of("ADVANCED", "ELITE"));
        def.set("display-name", "&eBonemerang Thrower");
        ConfigSection stats = def.getSection("stats");
        stats.set("HEALTH_MAX", 32);
        stats.set("ATTACK_DAMAGE", 6);
        stats.set("MOVE_SPEED", 100);
        stats.set("ARMOR", 1);
        def.set("main-hand-item", "BONEMERANG");
        def.set("ability-damage-multiplier", 0.5);
        def.set("ability-cast-interval", 80);
        def.set("mini-boss", true);
        def.set("boss-bar", true);
        ConfigSection armor = def.getSection("armor");
        armor.set("head", "SOME_HELMET");
        armor.set("chest", "SOME_CHESTPLATE");

        Map<String, MobDefinition> loaded = MobDefinitionLoader.loadAll(provider);

        assertEquals(1, loaded.size());
        MobDefinition mob = loaded.get("bonemerang-zombie");
        assertEquals("ZOMBIE", mob.getEntityType());
        assertEquals(4, mob.getWeight());
        assertEquals(Set.of(SpawnTier.ADVANCED, SpawnTier.ELITE), mob.getTiers());
        assertEquals("&eBonemerang Thrower", mob.getDisplayName());
        long now = System.currentTimeMillis();
        StatManager baseStats = mob.getBaseStats();
        assertEquals(32.0, baseStats.getCurrentValue(StatType.HEALTH_MAX, now), 0.001);
        assertEquals(32.0, baseStats.getCurrentValue(StatType.HEALTH_RESOURCE, now), 0.001, "spawns at full health");
        assertEquals(6.0, baseStats.getCurrentValue(StatType.ATTACK_DAMAGE, now), 0.001);
        assertEquals(100.0, baseStats.getCurrentValue(StatType.MOVE_SPEED, now), 0.001);
        assertEquals("BONEMERANG", mob.getMainHandItemId());
        assertEquals(0.5, mob.getAbilityDamageMultiplier(), 0.001);
        assertEquals(80, mob.getAbilityCastInterval());
        assertTrue(mob.isMiniBoss());
        assertTrue(mob.isBossBar());
        assertEquals("SOME_HELMET", mob.getArmor().get(EquipmentSlot.HEAD));
        assertEquals("SOME_CHESTPLATE", mob.getArmor().get(EquipmentSlot.CHEST));
    }

    @Test
    void loadAll_parsesEffects() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection def = provider.getRoot().getSection("dungeon-mobs").getSection("elite");
        def.set("entity-type", "WITHER_SKELETON");
        def.getSection("stats").set("HEALTH_MAX", 50);
        def.set("effects", List.of("BONE_SWING", "PARTICLE_TEST_ABILITY"));

        MobDefinition mob = MobDefinitionLoader.loadAll(provider).get("elite");

        assertEquals(2, mob.getEffects().size());
        assertEquals("BONE_SWING", mob.getEffects().get(0).effectId());
        assertEquals("PARTICLE_TEST_ABILITY", mob.getEffects().get(1).effectId());
    }

    @Test
    void loadAll_unknownTierFallsBackToAllTiers() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection def = provider.getRoot().getSection("dungeon-mobs").getSection("weird");
        def.set("entity-type", "ZOMBIE");
        def.set("tiers", List.of("BASIC", "NOT_A_TIER"));
        def.getSection("stats").set("HEALTH_MAX", 20);

        MobDefinition mob = MobDefinitionLoader.loadAll(provider).get("weird");

        assertTrue(mob.getTiers().contains(SpawnTier.BASIC), "valid tier must be kept");
        assertFalse(mob.getTiers().containsAll(Set.of(SpawnTier.ELITE)), "only BASIC should remain");
    }

    @Test
    void loadAll_skipsMobWithoutHealthMax() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection def = provider.getRoot().getSection("dungeon-mobs").getSection("broken");
        def.set("entity-type", "ZOMBIE");
        def.set("tiers", List.of("BASIC"));
        def.getSection("stats").set("ATTACK_DAMAGE", 5);

        Map<String, MobDefinition> loaded = MobDefinitionLoader.loadAll(provider);

        assertFalse(loaded.containsKey("broken"),
                "mob definition without HEALTH_MAX must be skipped, not spawned with 0 hp");
    }

    @Test
    void registry_indexesDefinitionsByTier() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection root = provider.getRoot().getSection("dungeon-mobs");
        ConfigSection basic = root.getSection("basic-zombie");
        basic.set("entity-type", "ZOMBIE");
        basic.set("tiers", List.of("BASIC"));
        basic.getSection("stats").set("HEALTH_MAX", 20);
        ConfigSection elite = root.getSection("elite-blaze");
        elite.set("entity-type", "BLAZE");
        elite.set("tiers", List.of("ELITE"));
        elite.getSection("stats").set("HEALTH_MAX", 40);

        MobDefinitionRegistry registry = MobDefinitionRegistry.getInstance();
        registry.registerAll(MobDefinitionLoader.loadAll(provider).values());

        assertEquals(2, registry.size());
        assertEquals(List.of("basic-zombie"), registry.getForTier(SpawnTier.BASIC).stream()
                .map(MobDefinition::getId).toList());
        assertEquals(List.of("elite-blaze"), registry.getForTier(SpawnTier.ELITE).stream()
                .map(MobDefinition::getId).toList());
        assertTrue(registry.getForTier(SpawnTier.ADVANCED).isEmpty(), "no mob configured for ADVANCED");
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
                        Map<String, Object> typed = new HashMap<>();
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            typed.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                        result.add(new TestConfigSection(typed));
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
    }
}