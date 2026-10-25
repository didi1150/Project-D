package dev.bukkit.entity.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import dev.bukkit.storage.BukkitConfigSection;
import dev.core.entity.boss.BossDefinition;
import dev.core.entity.boss.BossDefinitionLoader;
import dev.core.entity.boss.BossStage;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

class SealedEntityDefinitionTest {

    @Test
    void sealedEntityLoadsWithGrowAsItsInitialStage() throws Exception {
        Map<String, Object> raw;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("bosses.yml")) {
            assertNotNull(input, "bosses.yml must be on the test classpath");
            raw = new Yaml().load(input);
        }
        MemoryConfiguration config = new MemoryConfiguration();
        applyMap(config, raw);
        ConfigProvider provider = new ConfigProvider() {
            @Override
            public ConfigSection getRoot() {
                return new BukkitConfigSection(config);
            }

            @Override
            public ConfigSection getSection(String path) {
                return new BukkitConfigSection(config.getConfigurationSection(path));
            }

            @Override
            public void save() {
            }
        };

        List<BossDefinition> definitions = BossDefinitionLoader.loadAll(provider,
                new BukkitBossStageTypeRegistry(), new BukkitBossStrategyRegistry());
        BossDefinition sealed = definitions.stream()
                .filter(definition -> definition.getId().equals("sealed-entity"))
                .findFirst()
                .orElse(null);

        assertNotNull(sealed, "bosses.yml must define sealed-entity");
        assertEquals(1, sealed.getFloor());
        assertFalse(sealed.getStages().isEmpty(), "sealed-entity must have stages");
        BossStage initialStage = sealed.getStages().get(0);
        assertEquals("grow", initialStage.getId());
        assertEquals(2, sealed.getStages().size());
        assertEquals("wipe", sealed.getStages().get(1).getId());
    }

    @SuppressWarnings("unchecked")
    private static void applyMap(ConfigurationSection section, Map<?, ?> values) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (entry.getValue() instanceof Map<?, ?> child) {
                applyMap(section.createSection(key), child);
            } else {
                section.set(key, entry.getValue());
            }
        }
    }
}
