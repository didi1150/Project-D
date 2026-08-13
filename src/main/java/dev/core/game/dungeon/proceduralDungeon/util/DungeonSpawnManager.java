package dev.core.game.dungeon.proceduralDungeon.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.core.game.coords.Point3D;

public class DungeonSpawnManager {

    private static final DungeonSpawnManager INSTANCE = new DungeonSpawnManager();

    private final Map<ChunkKey, List<SpawnLocation>> spawnLocationsByChunk = new HashMap<>();
    private final Set<Point3D> consumedSpawnPoints = new HashSet<>();

    private DungeonSpawnManager() {
    }

    public static DungeonSpawnManager getInstance() {
        return INSTANCE;
    }

    public synchronized void reset() {
        spawnLocationsByChunk.clear();
        consumedSpawnPoints.clear();
    }

    public synchronized void registerSpawnLocation(SpawnLocation spawnLocation) {
        ChunkKey chunkKey = chunkKey(
                chunkFromBlock(spawnLocation.getPosition().getX()),
                chunkFromBlock(spawnLocation.getPosition().getY()),
                chunkFromBlock(spawnLocation.getPosition().getZ()));
        spawnLocationsByChunk.computeIfAbsent(chunkKey, key -> new ArrayList<>()).add(spawnLocation);
    }

    public synchronized void registerSpawnLocations(Collection<SpawnLocation> spawnLocations) {
        if (spawnLocations == null || spawnLocations.isEmpty()) {
            return;
        }
        for (SpawnLocation spawnLocation : spawnLocations) {
            registerSpawnLocation(spawnLocation);
        }
    }

    public synchronized List<SpawnLocation> getNearbyActiveSpawnLocations(int centerChunkX, int centerChunkY,
            int centerChunkZ, int radius) {
        List<SpawnLocation> active = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    ChunkKey key = chunkKey(centerChunkX + dx, centerChunkY + dy, centerChunkZ + dz);
                    List<SpawnLocation> chunkSpawns = spawnLocationsByChunk.get(key);
                    if (chunkSpawns == null) {
                        continue;
                    }
                    for (SpawnLocation spawnLocation : chunkSpawns) {
                        if (!consumedSpawnPoints.contains(spawnLocation.getPosition())) {
                            active.add(spawnLocation);
                        }
                    }
                }
            }
        }
        return active;
    }

    public synchronized boolean consumeSpawnLocation(SpawnLocation spawnLocation) {
        if (spawnLocation == null) {
            return false;
        }
        Point3D position = spawnLocation.getPosition();
        if (!consumedSpawnPoints.add(position)) {
            return false;
        }

        ChunkKey chunkKey = chunkKey(
                chunkFromBlock(position.getX()),
                chunkFromBlock(position.getY()),
                chunkFromBlock(position.getZ()));
        List<SpawnLocation> chunkList = spawnLocationsByChunk.get(chunkKey);
        if (chunkList != null) {
            chunkList.removeIf(spawn -> spawn.getPosition().equals(position));
            if (chunkList.isEmpty()) {
                spawnLocationsByChunk.remove(chunkKey);
            }
        }
        return true;
    }

    public static int chunkFromBlock(int blockCoordinate) {
        return blockCoordinate >> 4;
    }

    public static ChunkKey chunkKey(int chunkX, int chunkY, int chunkZ) {
        return new ChunkKey(chunkX, chunkY, chunkZ);
    }

    private static record ChunkKey(int x, int y, int z) {
    }
}
