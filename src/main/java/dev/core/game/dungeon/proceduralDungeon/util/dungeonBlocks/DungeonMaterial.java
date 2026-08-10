package dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class DungeonMaterial <Mat> {

    private final float placeProbability;
    private final List<Mat> materials = new LinkedList<>();
    private final List<DungeonMaterial<Mat>> dungeonMaterials = new LinkedList<>();

    @SafeVarargs
    public DungeonMaterial(float placeProbability, Mat ... materials) {
        this.placeProbability = placeProbability;
        this.materials.addAll(List.of(materials));
    }

    @SafeVarargs
    public DungeonMaterial(float placeProbability, DungeonMaterial<Mat> ... dungeonMaterials) {
        this.placeProbability = placeProbability;
        this.dungeonMaterials.addAll(List.of(dungeonMaterials));
    }

    /**
     * @param placeProbability Probability of using the given material variants, should be between 0.0 and 1.0
     * @param materials Material variants to choose a random from
     * @param <Mat> Material
     * @return DungeonMaterial with information on placeProbability of material variants
     */
    @SafeVarargs
    public static <Mat> DungeonMaterial<Mat> of(float placeProbability, Mat ... materials) {
        return new DungeonMaterial<>(placeProbability, materials);
    }

    /**
     * @param placeProbability Probability of using the given dungeonMaterials, should be between 0.0 and 1.0
     * @param dungeonMaterials DungeonMaterials to choose from
     * @param <Mat> Material
     * @return DungeonMaterial with information on placeProbability of material variants
     */
    @SafeVarargs
    public static <Mat> DungeonMaterial<Mat> of(float placeProbability, DungeonMaterial<Mat> ... dungeonMaterials) {
        return new DungeonMaterial<>(placeProbability, dungeonMaterials);
    }

    private static <Mat> DungeonMaterial<Mat> getDungeonMaterial(List<DungeonMaterial<Mat>> dungeonMaterials, Random random) {
        float start = 0;
        float ran = random.nextFloat(0, (float) dungeonMaterials.stream().mapToDouble(d -> d.placeProbability).sum());
        for (var dungeonMaterial : dungeonMaterials) {
            if (ran < (dungeonMaterial.placeProbability + start)) {
                return dungeonMaterial;
            }
            start += dungeonMaterial.placeProbability;
        }
        return dungeonMaterials.get(0);
    }

    public static <Mat> Mat getMaterial(List<DungeonMaterial<Mat>> dungeonMaterials, Random random) {
        return getDungeonMaterial(dungeonMaterials, random).getMaterial(random);
    }

    public List<Mat> getMaterials() {
        if (!dungeonMaterials.isEmpty()) {
            return dungeonMaterials.stream().flatMap(d -> d.getMaterials().stream()).collect(Collectors.toList());
        }
        return materials;
    }

    public Mat getMaterial(Random random) {
        if (!dungeonMaterials.isEmpty()) {
            return getMaterial(dungeonMaterials, random);
        }
        return materials.get(random.nextInt(0, materials.size()));
    }

}
