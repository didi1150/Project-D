package dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks;

public class DungeonDecorationMaterial <Mat> extends DungeonMaterial<Mat> {

    private final DungeonDecorationType type;

    @SafeVarargs
    public DungeonDecorationMaterial(DungeonDecorationType type, float placeProbability, Mat... materials) {
        super(placeProbability, materials);
        this.type = type;
    }

    @SafeVarargs
    public DungeonDecorationMaterial(DungeonDecorationType type, float placeProbability, DungeonMaterial<Mat>... dungeonMaterials) {
        super(placeProbability, dungeonMaterials);
        this.type = type;
    }

    @SafeVarargs
    public DungeonDecorationMaterial(float placeProbability, Mat... materials) {
        super(placeProbability, materials);
        this.type = DungeonDecorationType.INDIVIDUAL;
    }

    @SafeVarargs
    public DungeonDecorationMaterial(float placeProbability, DungeonMaterial<Mat>... dungeonMaterials) {
        super(placeProbability, dungeonMaterials);
        this.type = DungeonDecorationType.INDIVIDUAL;
    }

    /**
     * @param type Type of the decoration material
     * @param placeProbability Probability of using the given material variants, should be between 0.0 and 1.0
     * @param materials Material variants to choose a random from
     * @param <Mat> Material
     * @return DungeonMaterial with information on placeProbability of material variants
     */
    @SafeVarargs
    public static <Mat> DungeonDecorationMaterial<Mat> of(DungeonDecorationType type, float placeProbability, Mat ... materials) {
        return new DungeonDecorationMaterial<>(type, placeProbability, materials);
    }

    /**
     * @param type Type of the decoration material
     * @param placeProbability Probability of using the given dungeonMaterials, should be between 0.0 and 1.0
     * @param dungeonMaterials DungeonMaterials to choose from
     * @param <Mat> Material
     * @return DungeonMaterial with information on placeProbability of material variants
     */
    @SafeVarargs
    public static <Mat> DungeonDecorationMaterial<Mat> of(DungeonDecorationType type, float placeProbability, DungeonMaterial<Mat> ... dungeonMaterials) {
        return new DungeonDecorationMaterial<>(type, placeProbability, dungeonMaterials);
    }

    /**
     * @param placeProbability Probability of using the given material variants, should be between 0.0 and 1.0
     * @param materials Material variants to choose a random from
     * @param <Mat> Material
     * @return DungeonMaterial with information on placeProbability of material variants
     */
    @SafeVarargs
    public static <Mat> DungeonDecorationMaterial<Mat> of(float placeProbability, Mat ... materials) {
        return new DungeonDecorationMaterial<>(placeProbability, materials);
    }

    /**
     * @param placeProbability Probability of using the given dungeonMaterials, should be between 0.0 and 1.0
     * @param dungeonMaterials DungeonMaterials to choose from
     * @param <Mat> Material
     * @return DungeonMaterial with information on placeProbability of material variants
     */
    @SafeVarargs
    public static <Mat> DungeonDecorationMaterial<Mat> of(float placeProbability, DungeonMaterial<Mat> ... dungeonMaterials) {
        return new DungeonDecorationMaterial<>(placeProbability, dungeonMaterials);
    }

    public DungeonDecorationType getType() {
        return type;
    }
}
