package dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks;

import dev.core.game.dungeon.proceduralDungeon.util.Direction3D;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;

public class DungeonStairBlock extends DungeonBlock {

    private Direction3D facingDirection;

    public DungeonStairBlock(Vector3Int pos, Direction3D facingDirection) {
        super(pos);
        this.facingDirection = facingDirection;
    }

    public Direction3D getFacingDirection() {
        return facingDirection;
    }

    public String getFacingDirectionAsString() {
        return facingDirection.name();
    }
}
