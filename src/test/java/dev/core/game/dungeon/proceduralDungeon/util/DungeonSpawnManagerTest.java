package dev.core.game.dungeon.proceduralDungeon.util;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.game.coords.Point3D;

class DungeonSpawnManagerTest {

    @BeforeEach
    void beforeEach() {
        DungeonSpawnManager.getInstance().reset();
    }

    @Test
    void shouldRegisterAndConsumeNearbySpawnLocationsIn3D() {
        // Keep the second spawn in a different chunk so the radius-based query isolates just the first spawn.
        SpawnLocation spawn1 = new SpawnLocation(new Point3D(8, 8, 8), SpawnTier.BASIC, 0.8, 1, false);
        SpawnLocation spawn2 = new SpawnLocation(new Point3D(40, 12, 40), SpawnTier.BASIC, 0.8, 1, false);

        DungeonSpawnManager.getInstance().registerSpawnLocations(List.of(spawn1, spawn2));

        List<SpawnLocation> nearby = DungeonSpawnManager.getInstance().getNearbyActiveSpawnLocations(0, 0, 0, 1);
        assertEquals(1, nearby.size());
        assertTrue(nearby.stream().anyMatch(s -> s.getPosition().equals(spawn1.getPosition())));

        assertTrue(DungeonSpawnManager.getInstance().consumeSpawnLocation(spawn1));

        List<SpawnLocation> remaining = DungeonSpawnManager.getInstance().getNearbyActiveSpawnLocations(0, 0, 0, 2);
        assertEquals(1, remaining.size());
        assertTrue(remaining.stream().anyMatch(s -> s.getPosition().equals(spawn2.getPosition())));

        assertTrue(DungeonSpawnManager.getInstance().consumeSpawnLocation(spawn2));

        List<SpawnLocation> emptyCheck = DungeonSpawnManager.getInstance().getNearbyActiveSpawnLocations(0, 0, 0, 1);
        assertEquals(0, emptyCheck.size());
    }
}
