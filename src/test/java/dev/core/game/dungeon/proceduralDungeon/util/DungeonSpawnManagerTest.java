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
        // Spawn 1 at (X=16, Y=65, Z=16) - Chunk Key (0, 0, 0)
        SpawnLocation spawn1 = new SpawnLocation(new Point3D(16, 65, 16), SpawnTier.BASIC, 0.8, 1, false);
        // Spawn 2 at (X=30, Y=70, Z=30) - Chunk Key (1, 2, 1)
        SpawnLocation spawn2 = new SpawnLocation(new Point3D(30, 70, 30), SpawnTier.BASIC, 0.8, 1, false);

        // Register spawns in different chunks/levels
        DungeonSpawnManager.getInstance().registerSpawnLocations(List.of(spawn1, spawn2));

        // Test retrieval from a chunk near spawn1 (e.g., center of the same chunk)
        List<SpawnLocation> nearby = DungeonSpawnManager.getInstance().getNearbyActiveSpawnLocations(0, 0, 0, 1);
        assertEquals(1, nearby.size());
        assertTrue(nearby.stream().anyMatch(s -> s.getPosition().equals(spawn1.getPosition())));

        // Consume spawn1
        assertTrue(DungeonSpawnManager.getInstance().consumeSpawnLocation(spawn1));

        // Test retrieval again: should only find spawn2 (if radius is large enough) or nothing if we query the wrong chunk area.
        // Let's query a central point that covers both chunks, e.g., (0, 0, 0) with radius 2.
        List<SpawnLocation> remaining = DungeonSpawnManager.getInstance().getNearbyActiveSpawnLocations(0, 0, 0, 2);
        assertEquals(1, remaining.size());
        assertTrue(remaining.stream().anyMatch(s -> s.getPosition().equals(spawn2.getPosition())));

        // Consume spawn2
        assertTrue(DungeonSpawnManager.getInstance().consumeSpawnLocation(spawn2));

        // Final check: nothing should be found
        List<SpawnLocation> emptyCheck = DungeonSpawnManager.getInstance().getNearbyActiveSpawnLocations(0, 0, 0, 1);
        assertEquals(0, emptyCheck.size());
    }
}
