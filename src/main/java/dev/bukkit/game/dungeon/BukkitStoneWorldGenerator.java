package dev.bukkit.game.dungeon;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

public class BukkitStoneWorldGenerator extends ChunkGenerator {

    private final BlockData stone = Material.STONE.createBlockData();

    @Override
    public void generateBedrock(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
    }

    @Override
    public void generateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // Fill the entire chunk from the world's min height up to max height with stone
        int minY = worldInfo.getMinHeight();
        int maxY = worldInfo.getMaxHeight();

        chunkData.setRegion(0, minY, 0, 16, maxY, 16, stone);
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return Collections.emptyList();
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, 65, 0.5);
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return false;
    }

}
