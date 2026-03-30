package dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks;

import dev.core.game.dungeon.proceduralDungeon.util.Direction3D;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;

import java.util.Set;

public class DungeonFloorBlock extends DungeonBlock {
    public DungeonFloorBlock(Vector3Int pos) {
        super(pos);
    }

    @Override
    public boolean canAttachDecorationOnSide(Set<Vector3Int> allPositions) {
        return !allPositions.contains(Direction3D.UP.apply(pos));
    }

    public boolean isValidCorner(Set<Vector3Int> allPositions) {
        for (var firstDir : Direction3D.get2DCardinalDirections()) {
            Direction3D secondDir = firstDir.rotateToRight();
            if (!allPositions.contains(firstDir.apply(pos)) && !allPositions.contains(secondDir.apply(pos))) {
                return true;
            }
        }
        return false;
    }

    public Direction3D[] getCornerDirections(Set<Vector3Int> floorPositions) {
        Direction3D[] dirs = new Direction3D[2];
        for (var firstDir : Direction3D.get2DCardinalDirections()) {
            Direction3D secondDir = firstDir.rotateToRight();
            if (!floorPositions.contains(firstDir.apply(pos)) && !floorPositions.contains(secondDir.apply(pos))) {
                if (floorPositions.contains(firstDir.opposite().apply(pos))) dirs[0] = firstDir.opposite();
                if (floorPositions.contains(secondDir.opposite().apply(pos))) dirs[1] = secondDir.opposite();
            }
        }
        return dirs;
    }

    public int getAverageCornerLength(Set<Vector3Int> floorPositions, Set<Vector3Int> wallPositions) {
        Direction3D[] cornerDirs = this.getCornerDirections(floorPositions);
        Vector3Int currentPos = this.getPos();
        int index = 0;
        int length = 0;
        while (index < cornerDirs.length) {
            Direction3D currentDir = cornerDirs[index];
            if (currentDir == null) {
                index++;
                continue;
            }
            currentPos = currentDir.apply(currentPos);
            length++;

            boolean wallOnSide = (index + 1) == cornerDirs.length ? wallPositions.contains(currentDir.rotateToRight().apply(currentPos)) : wallPositions.contains(currentDir.rotateToLeft().apply(currentPos));

            if (!floorPositions.contains(currentPos) || !wallOnSide) {
                currentPos = this.getPos();
                index++;
            }
        }
        return length / cornerDirs.length;
    }
}
