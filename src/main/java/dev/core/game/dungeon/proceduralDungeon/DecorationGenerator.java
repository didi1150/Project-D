package dev.core.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.BoundingBox;
import dev.core.game.dungeon.Direction;
import dev.core.game.dungeon.proceduralDungeon.util.Direction3D;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.*;

import java.util.*;
import java.util.stream.Collectors;

public class DecorationGenerator {


    public static List<Set<DungeonDecorationBlock>> generateVines(Set<DungeonWallBlock> wallBlocks, Set<DungeonBlock> allBlocks, int numberVines, int vineLength, Random random) {
        List<Set<DungeonDecorationBlock>> vines = new LinkedList<>();

        Set<Vector3Int> allPositions = allBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toSet());
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

            List<DungeonWallBlock> validWallBlocksForDir = validWallBlocks.stream().filter(wall -> wall.canAttachDecorationOnSide(dir, allPositions)).toList();

            var path = ProceduralGenerationAlgorithms.simpleRandomWalkWithValidBlocks(startPos, vineLength, validWallBlocksForDir, directions, random);
            vines.add(path.stream().map(pos -> new DungeonDecorationBlock(dir.apply(pos), dir, DungeonDecorationType.SPREADING, DungeonDecorationPlacementType.WALL)).collect(Collectors.toSet()));
            validWallBlocks.removeIf(wall -> path.contains(wall.getPos()));

            vineBoxes.add(new BoundingBox(path.toArray(Vector3Int[]::new)));

            System.out.println("Generated Vine at: " + startBlock + " with path: " + path);
        }
        return vines;
    }
}
