package dev.bukkit.utils.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import dev.bukkit.entity.boss.stage.BossGrowStageLocations;
import dev.bukkit.storage.BukkitConfigSection;
import dev.core.game.coords.Point3D;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

class BossStageLocationConfigTest {

    private static final int FLOOR = 4200;

    @Test
    void writesAndRemovesLocationsOnTheGrowStage() {
        MemoryConfiguration handle = new MemoryConfiguration();
        handle.set("bosses." + FLOOR + ".test-boss.stages", List.of(
                Map.of("type", "GROW", "id", "grow", "pillar-count", 4),
                Map.of("type", "WIPE", "id", "wipe")));
        ConfigProvider provider = new TestConfigProvider(handle);
        BossStageLocationConfig config = new BossStageLocationConfig(provider, handle);

        Point3D pillar = new Point3D(1, 2, 3, "boss");
        Point3D chain = new Point3D(4, 5, 6, "boss");
        Point3D crate = new Point3D(7, 8, 9, "boss");

        assertTrue(config.setPillarLocation(FLOOR, 1, pillar));
        assertTrue(config.setChainLinkLocation(FLOOR, 1, chain));
        assertTrue(config.addCrateSpawnerLocation(FLOOR, crate));

        Optional<BossGrowStageLocations> loaded = config.load(FLOOR);
        assertTrue(loaded.isPresent());
        assertEquals(pillar, loaded.get().pillarLocations().get(1));
        assertEquals(chain, loaded.get().chainLinkLocations().get(1));
        assertTrue(loaded.get().crateSpawnerLocations().contains(crate));

        assertFalse(config.addCrateSpawnerLocation(FLOOR, crate));
        assertTrue(config.removePillarLocation(FLOOR, pillar));
        assertTrue(config.load(FLOOR).get().pillarLocations().isEmpty());
        assertTrue(config.load(FLOOR).get().chainLinkLocations().containsKey(1));
    }

    @Test
    void migratesLegacySetupLocationsIntoTheGrowStage() {
        MemoryConfiguration bossesHandle = new MemoryConfiguration();
        bossesHandle.set("bosses." + FLOOR + ".test-boss.stages", List.of(
                Map.of("type", "GROW", "id", "grow", "pillar-count", 4)));
        MemoryConfiguration setupHandle = new MemoryConfiguration();
        setupHandle.set("bosssettings.floor" + FLOOR + ".pillarlocations.1.x", 1);
        setupHandle.set("bosssettings.floor" + FLOOR + ".pillarlocations.1.y", 2);
        setupHandle.set("bosssettings.floor" + FLOOR + ".pillarlocations.1.z", 3);
        setupHandle.set("bosssettings.floor" + FLOOR + ".cratespawnerlocations",
                List.of(Map.of("x", 7, "y", 8, "z", 9)));

        ConfigProvider setupProvider = new TestConfigProvider(setupHandle);
        BossStageLocationConfig config = new BossStageLocationConfig(
                new TestConfigProvider(bossesHandle), bossesHandle);

        int migrated = BossStageLocationMigration.migrate(config, setupProvider, FLOOR);

        assertEquals(2, migrated);
        assertTrue(config.load(FLOOR).get().pillarLocations().containsKey(1));
        assertTrue(config.load(FLOOR).get().crateSpawnerLocations().contains(new Point3D(7, 8, 9)));
        assertFalse(setupHandle.contains("bosssettings.floor" + FLOOR + ".pillarlocations"));
        assertFalse(setupHandle.contains("bosssettings.floor" + FLOOR + ".cratespawnerlocations"));
    }

    private static final class TestConfigProvider implements ConfigProvider {
        private final MemoryConfiguration handle;

        private TestConfigProvider(MemoryConfiguration handle) {
            this.handle = handle;
        }

        @Override
        public ConfigSection getRoot() {
            return new BukkitConfigSection(handle);
        }

        @Override
        public ConfigSection getSection(String path) {
            return new BukkitConfigSection(handle.getConfigurationSection(path));
        }

        @Override
        public void save() {
        }
    }
}
