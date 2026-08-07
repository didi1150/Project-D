package dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks;

import dev.core.game.dungeon.proceduralDungeon.util.Direction3D;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;

import java.util.Set;

public class DungeonCeilingBlock extends DungeonBlock {
    public DungeonCeilingBlock(Vector3Int pos) {
        super(pos);
    }

    @Override
    public boolean canAttachDecorationOnSide(Set<Vector3Int> allPositions) {
        return !allPositions.contains(Direction3D.DOWN.apply(pos));
    }

    public int getMaxSpaceForDecoration(int maxLength, Set<Vector3Int> allPositions) {
        Vector3Int pos = Direction3D.DOWN.apply(getPos());
        int length = 0;
        while (!allPositions.contains(pos) && length < maxLength) {
            pos = Direction3D.DOWN.apply(pos);
            length++;
        }
        return length;
    }
}
