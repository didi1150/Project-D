package dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks;

import dev.core.game.dungeon.proceduralDungeon.util.Direction3D;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;

import java.util.Set;

public class DungeonDecorationBlock extends DungeonBlock {

    private final DungeonDecorationType decorationType;
    private final DungeonDecorationPlacementType placementType;
    
    private final Direction3D facingDirection;

    public DungeonDecorationBlock(Vector3Int pos, Direction3D facingDirection, DungeonDecorationType decorationType, DungeonDecorationPlacementType placementType) {
        super(pos);
        this.facingDirection = facingDirection;
        this.decorationType = decorationType;
        this.placementType = placementType;
    }

    public DungeonDecorationBlock(Vector3Int pos, DungeonDecorationType decorationType, DungeonDecorationPlacementType placementType) {
        super(pos);
        this.decorationType = decorationType;
        this.placementType = placementType;
        this.facingDirection = null;
    }

    public DungeonDecorationBlock(Vector3Int pos) {
        super(pos);
        this.decorationType = DungeonDecorationType.INDIVIDUAL;
        this.placementType = DungeonDecorationPlacementType.FLOOR;
        this.facingDirection = null;
    }

    @Override
    public boolean canAttachDecorationOnSide(Set<Vector3Int> allPositions) {
        return false;
    }

    public DungeonDecorationType getDecorationType() {
        return decorationType;
    }

    public DungeonDecorationPlacementType getPlacementType() {
        return placementType;
    }

    public String getFacingDirectionAsString() {
        return facingDirection.name();
    }
}
