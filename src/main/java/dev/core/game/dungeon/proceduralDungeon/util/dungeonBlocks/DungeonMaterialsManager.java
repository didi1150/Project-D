package dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public abstract class DungeonMaterialsManager <Mat> {

    protected Random random;

    public DungeonMaterialsManager(long seed) {
        this.random = new Random(seed);
    }

    public abstract List<DungeonMaterial<Mat>> getDungeonFloorBlockMaterials();

    public abstract List<DungeonMaterial<Mat>> getDungeonStairBlockMaterials();

    public abstract List<DungeonMaterial<Mat>> getDungeonWallBlockMaterials();

    public abstract List<DungeonMaterial<Mat>> getDungeonCeilingBlockMaterials();

    public abstract List<DungeonMaterial<Mat>> getDungeonLightBlockMaterials();

    public abstract List<DungeonMaterial<Mat>> getDungeonFloorDecorationBlockMaterials();

    public abstract List<DungeonMaterial<Mat>> getDungeonWallDecorationBlockMaterials();

    public abstract List<DungeonMaterial<Mat>> getDungeonCeilingDecorationBlockMaterials();

    public abstract List<DungeonMaterial<Mat>> getDungeonRoomCornerDecorationBlockMaterials();

    public List<DungeonMaterial<Mat>> getMaterialList(DungeonBlock dungeonBlock) {
        if (dungeonBlock instanceof DungeonFloorBlock) {
            return getDungeonFloorBlockMaterials();
        }
        if (dungeonBlock instanceof DungeonStairBlock) {
            return getDungeonStairBlockMaterials();
        }
        if (dungeonBlock instanceof DungeonWallBlock) {
            return getDungeonWallBlockMaterials();
        }
        if (dungeonBlock instanceof DungeonCeilingBlock) {
            return getDungeonCeilingBlockMaterials();
        }
        if (dungeonBlock instanceof DungeonDecorationBlock decorationBlock) {
            switch (decorationBlock.getPlacementType()) {
                case FLOOR -> {
                    return getDungeonFloorDecorationBlockMaterials();
                }
                case WALL -> {
                    return getDungeonWallDecorationBlockMaterials();
                }
                case CEILING -> {
                    return getDungeonCeilingDecorationBlockMaterials();
                }
                case CORNER -> {
                    return getDungeonRoomCornerDecorationBlockMaterials();
                }
            }
        }
        return new LinkedList<>();
    }

    public Mat getMaterial(DungeonBlock dungeonBlock) {
        //TODO only temporary for testing
        List<DungeonMaterial<Mat>> matList = getMaterialList(dungeonBlock);
        if (dungeonBlock instanceof DungeonDecorationBlock decorationBlock) {
            matList = matList.stream().filter(mat -> ((DungeonDecorationMaterial<Mat>) mat).getType() == decorationBlock.getDecorationType()).toList();
        }
        return DungeonMaterial.getMaterial(matList, random);
//        int i = matList.size() / 3;
//        if (random.nextFloat(0,1) < 0.75) {
//            return matList.get(random.nextInt(0, i));
//        } else {
//            return matList.get(random.nextInt(i, matList.size()));
//        }
    }

    public abstract void placeBlock(DungeonBlock dungeonBlock);

    public abstract void placeBlocksWithSameMaterial(LinkedHashSet<? extends DungeonBlock> dungeonBlocks);
}
