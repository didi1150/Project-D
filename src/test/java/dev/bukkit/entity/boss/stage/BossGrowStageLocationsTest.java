package dev.bukkit.entity.boss.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import dev.bukkit.storage.BukkitConfigSection;
import dev.core.game.coords.Point3D;

class BossGrowStageLocationsTest {

    @Test
    void readsNumberedAndListLocationsFromStageParams() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("pillar-locations.1.x", 10);
        config.set("pillar-locations.1.y", 64);
        config.set("pillar-locations.1.z", 10);
        config.set("pillar-locations.1.world", "boss");
        config.set("chain-link-locations.1.x", 15);
        config.set("chain-link-locations.1.y", 70);
        config.set("chain-link-locations.1.z", 10);
        config.set("crate-spawner-locations",
                List.of(Map.of("x", 5, "y", 64, "z", 5, "world", "boss")));
        config.set("stalactite-locations",
                List.of(Map.of("x", 0, "y", 80, "z", 0, "world", "boss")));
        config.set("mob-wave-spawn-locations",
                List.of(Map.of("x", -5, "y", 64, "z", 5, "world", "boss")));

        BossGrowStageLocations locations = BossGrowStageLocations.from(new BukkitConfigSection(config));

        assertEquals(10, locations.pillarLocations().get(1).getX());
        assertEquals("boss", locations.pillarLocations().get(1).getWorld());
        assertEquals(15, locations.chainLinkLocations().get(1).getX());
        assertTrue(locations.crateSpawnerLocations().contains(new Point3D(5, 64, 5)));
        assertTrue(locations.stalactiteLocations().contains(new Point3D(0, 80, 0)));
        assertTrue(locations.mobWaveSpawnLocations().contains(new Point3D(-5, 64, 5)));
    }

    @Test
    void missingKeysProduceEmptyLocations() {
        BossGrowStageLocations locations = BossGrowStageLocations.from(
                new BukkitConfigSection(new MemoryConfiguration()));

        assertTrue(locations.pillarLocations().isEmpty());
        assertTrue(locations.chainLinkLocations().isEmpty());
        assertTrue(locations.crateSpawnerLocations().isEmpty());
        assertTrue(locations.stalactiteLocations().isEmpty());
        assertTrue(locations.mobWaveSpawnLocations().isEmpty());
    }
}
