package dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks;

import dev.core.game.dungeon.proceduralDungeon.util.Direction3D;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;

import javax.annotation.Nullable;
import java.util.Set;

public class DungeonWallBlock extends DungeonBlock {

    @Nullable
    private final Direction3D facingDirection;

    public DungeonWallBlock(Vector3Int pos, @Nullable Direction3D facingDirection) {
        super(pos);
        this.facingDirection = facingDirection;
    }

//    public DungeonWallBlock(Vector3Int pos, Vector3Int wallPos) {
//        super(pos);
//        this.facingDirection = Direction3D.getDirectionForVec(pos.sub(wallPos));
//    }

    @Nullable
    public Direction3D getFacingDirection() {
        return facingDirection;
    }

    public boolean canAttachDecorationOnSide(Set<Vector3Int> allPositions) {
        return canAttachDecorationOnSide(this.facingDirection, allPositions);
    }

    public boolean canAttachDecorationOnSide(Direction3D facingDirection, Set<Vector3Int> allPositions) {
        return facingDirection != null && !allPositions.contains(facingDirection.apply(pos));
    }

    @Override
    public String toString() {
        return "DungeonWallBlock{pos=" + pos + ", facingDirection=" + facingDirection + "}";
    }
}
