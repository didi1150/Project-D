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
}
