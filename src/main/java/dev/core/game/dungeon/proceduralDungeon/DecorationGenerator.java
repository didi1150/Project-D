package dev.core.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.BoundingBox;
import dev.core.game.dungeon.proceduralDungeon.util.Direction3D;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.*;

import java.util.*;
import java.util.stream.Collectors;

public class DecorationGenerator {


    public static List<Set<DungeonDecorationBlock>> generateVines(Set<DungeonWallBlock> wallBlocks, Set<Vector3Int> allPositions, int numberVines, int vineLength, Random random) {
        List<Set<DungeonDecorationBlock>> vines = new LinkedList<>();

        List<DungeonWallBlock> validWallBlocks = wallBlocks.stream().filter(wall -> wall.canAttachDecorationOnSide(allPositions)).collect(Collectors.toList());

        List<BoundingBox> vineBoxes = new LinkedList<>();
        double minDistanceApart = vineLength;

        System.out.println("Generating " + numberVines + " Vines with a maxLength of " + vineLength);

        for (var i = 0; i < numberVines; i++) {
            DungeonWallBlock startBlock;
            boolean validStartPos;
            int tries = 0;
            do {
                startBlock = validWallBlocks.get(random.nextInt(0, validWallBlocks.size()));
                Vector3Int startPos = startBlock.getPos();
                validStartPos = !vineBoxes.isEmpty() && vineBoxes.stream().anyMatch(box -> box.get3DCenter().distance(startPos) < (minDistanceApart + box.getDimensions().mul(0.5).getLength()));
                tries++;
            } while (validStartPos && tries < validWallBlocks.size());
            Direction3D dir = startBlock.getFacingDirection();
            Vector3Int startPos = startBlock.getPos();

            List<Direction3D> directions = Direction3D.get2DCardinalDirections();
            directions.removeAll(List.of(dir, dir.opposite()));
            directions.addAll(List.of(Direction3D.UP, Direction3D.DOWN));

            List<Vector3Int> validWallBlocksForDir = validWallBlocks.stream().filter(wall -> wall.canAttachDecorationOnSide(dir, allPositions)).map(DungeonBlock::getPos).toList();

            var path = ProceduralGenerationAlgorithms.simpleRandomWalkWithValidBlocks(startPos, vineLength, validWallBlocksForDir, directions, random);
            vines.add(path.stream().map(pos -> new DungeonDecorationBlock(dir.apply(pos), dir, DungeonDecorationType.SPREADING, DungeonDecorationPlacementType.WALL)).collect(Collectors.toSet()));
            validWallBlocks.removeIf(wall -> path.contains(wall.getPos()));

            vineBoxes.add(new BoundingBox(path.toArray(Vector3Int[]::new)));

//            System.out.println("Generated Vine at: " + startBlock + " with length: " + path.size());
        }
        return vines;
    }

    public static List<Set<DungeonDecorationBlock>> generateIndividualFloorVegetation(Set<DungeonFloorBlock> floorBlocks, Set<Vector3Int> allPositions, float spawnChance, Random random) {

        List<Set<DungeonDecorationBlock>> vegetation = new LinkedList<>();

        List<DungeonFloorBlock> validBlocks = floorBlocks.stream().filter(block -> block.canAttachDecorationOnSide(allPositions)).toList();

        List<Vector3Int> placedPositions = new LinkedList<>();

        int minDistanceApart = 5;

        for (var floorBlock : validBlocks) {
            if (placedPositions.stream().anyMatch(pos -> pos.distance(floorBlock.getPos()) < minDistanceApart)) {
                continue;
            }
            if (random.nextFloat() < spawnChance) {
                Set<DungeonDecorationBlock> deco = new LinkedHashSet<>(List.of(new DungeonDecorationBlock(Direction3D.UP.apply(floorBlock.getPos()), DungeonDecorationType.INDIVIDUAL, DungeonDecorationPlacementType.FLOOR)));
                vegetation.add(deco);
                placedPositions.add(floorBlock.getPos());
            }
        }

        System.out.println("Generated " + vegetation.size() + " individual floor vegetation");

        Set<DungeonDecorationBlock> set = vegetation.stream().flatMap(Collection::stream).collect(Collectors.toCollection(LinkedHashSet::new));
        float maxBundleSize = set.size() * 0.1F;
        List<Set<DungeonDecorationBlock>> vegetationBundles = new LinkedList<>();
        for (var block : new LinkedHashSet<>(set)) {
            int maxSize = (int) (maxBundleSize * random.nextFloat(0.2F, 0.4F));
            Set<DungeonDecorationBlock> blocks = set.stream().filter(b -> b.getPos().distance(block.getPos()) < (minDistanceApart*2)).limit(maxSize).collect(Collectors.toCollection(LinkedHashSet::new));
            if (!blocks.isEmpty()) {
                vegetationBundles.add(blocks);
                set.removeAll(blocks);
            }
        }

        System.out.println("Generated " + vegetationBundles.size() + " different individual floor vegetation bundles -> sizes: " + vegetationBundles.stream().mapToInt(Set::size).min().orElse(0) + " - " + vegetationBundles.stream().mapToInt(Set::size).max().orElse(0));

        return vegetationBundles;
    }

    public static List<Set<DungeonDecorationBlock>> generateHangingCeilingVegetation(Set<DungeonCeilingBlock> ceilingBlocks, Set<Vector3Int> allPositions, float spawnChance, int dungeonHeight, Random random) {

        List<Set<DungeonDecorationBlock>> vegetation = new LinkedList<>();

        List<DungeonCeilingBlock> validBlocks = ceilingBlocks.stream().filter(block -> block.canAttachDecorationOnSide(allPositions)).toList();

        List<Vector3Int> placedPositions = new LinkedList<>();

        int minDistanceApart = 5;

        for (var ceilingBlock : validBlocks) {
            if (placedPositions.stream().anyMatch(pos -> pos.distance(ceilingBlock.getPos()) < minDistanceApart)) {
                continue;
            }
            if (random.nextFloat() < spawnChance) {
                int maxLength = ceilingBlock.getMaxSpaceForDecoration(dungeonHeight, allPositions);
                int length = maxLength / 2;
                int randomLength = random.nextInt(1, length == 0 ? 2 : length + 1);
                Set<DungeonDecorationBlock> deco = new LinkedHashSet<>();
                Vector3Int pos = ceilingBlock.getPos();
                for (int i = 0; i < randomLength; i++) {
                    pos = Direction3D.DOWN.apply(pos);
                    deco.add(new DungeonDecorationBlock(pos, DungeonDecorationType.HANGING, DungeonDecorationPlacementType.CEILING));
                }
                vegetation.add(deco);
                placedPositions.add(ceilingBlock.getPos());
            }
        }

        System.out.println("Generated " + vegetation.size() + " hanging ceiling vegetation");

        return vegetation;
    }

    public static List<Set<DungeonDecorationBlock>> generateCornerVegetation(Set<DungeonFloorBlock> floorBlocks, Set<DungeonWallBlock> wallBlocks, Set<Vector3Int> allPositions, float spawnChance, Random random) {
        List<Set<DungeonDecorationBlock>> vegetation = new LinkedList<>();

        List<DungeonFloorBlock> validBlocks = floorBlocks.stream().filter(block -> block.canAttachDecorationOnSide(allPositions)).collect(Collectors.toList());

        Set<Vector3Int> floorPositions = floorBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toSet());

        Set<Vector3Int> wallPositions = wallBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toSet());

        validBlocks = validBlocks.stream().filter(block -> block.isValidCorner(floorPositions)).toList();

        List<BoundingBox> boxes = new LinkedList<>();
        double minDistanceApart = 5;

        for (var floorBlock : validBlocks) {
            if (boxes.stream().anyMatch(box -> box.get3DCenter().distance(floorBlock.getPos()) < (minDistanceApart + box.getDimensions().mul(0.5).getLength()))) {
                continue;
            }
            if (random.nextFloat() < spawnChance) {
                Direction3D[] cornerDirs = floorBlock.getCornerDirections(floorPositions);
                List<Direction3D> directions = Arrays.stream(cornerDirs).filter(Objects::nonNull).collect(Collectors.toList());
                int maxLength = floorBlock.getAverageCornerLength(floorPositions, wallPositions);
                int length = Math.max(maxLength / 2, 1);
                int randomLength = random.nextInt(1, length + 1);
                randomLength = length;
                int iterations = Math.max(length * 3, 3);
                Vector3Int startPos = Direction3D.UP.apply(floorBlock.getPos());
                Set<Vector3Int> path = new LinkedHashSet<>();
                for (int i = 0; i < iterations; i++) {
                    if (i == iterations / 5) directions.add(Direction3D.UP);
                    path.addAll(ProceduralGenerationAlgorithms.simpleRandomWalkWithInValidBlocks(startPos, randomLength, allPositions, directions, random));
                }
                vegetation.add(path.stream().map(pos -> new DungeonDecorationBlock(pos, DungeonDecorationType.BULK, DungeonDecorationPlacementType.CORNER)).collect(Collectors.toCollection(LinkedHashSet::new)));
                boxes.add(new BoundingBox(path.toArray(Vector3Int[]::new)));
//                System.out.println("Generated corner vegetation at " + startPos + " with length = " + randomLength + " and iterations = " + iterations);
            }
        }

        System.out.println("Generated " + vegetation.size() + " corner vegetation");

        return vegetation;
    }
}
