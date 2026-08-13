package dev.bukkit.game.dungeon;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Material;

public class BlockPlacer {

    private final Map<Material, Integer> materialWeights;
    private final Random random;

    public BlockPlacer() {
        this.random = new Random();
        this.materialWeights = new HashMap<>();
        initializeMaterials();
    }

    private void initializeMaterials() {
        // Floor materials
        materialWeights.put(Material.STONE_BRICKS, 30);
        materialWeights.put(Material.COBBLESTONE, 25);
        materialWeights.put(Material.MOSSY_STONE_BRICKS, 20);
        materialWeights.put(Material.CRACKED_STONE_BRICKS, 15);
        materialWeights.put(Material.ANDESITE, 10);
    }

    public Material getRandomFloorMaterial() {
        return getWeightedRandomMaterial();
    }

    public Material getRandomWallMaterial() {
        return getWeightedRandomMaterial();
    }

    public Material getRandomRoofMaterial() {
        List<Material> roofMaterials = Arrays.asList(Material.STONE_BRICK_SLAB, Material.COBBLESTONE_SLAB,
                Material.STONE_SLAB);
        return roofMaterials.get(random.nextInt(roofMaterials.size()));
    }

    private Material getWeightedRandomMaterial() {
        int totalWeight = materialWeights.values().stream().mapToInt(Integer::intValue).sum();
        int randomWeight = random.nextInt(totalWeight);

        for (Map.Entry<Material, Integer> entry : materialWeights.entrySet()) {
            randomWeight -= entry.getValue();
            if (randomWeight < 0) {
                return entry.getKey();
            }
        }

        return Material.STONE; // Fallback
    }
}
