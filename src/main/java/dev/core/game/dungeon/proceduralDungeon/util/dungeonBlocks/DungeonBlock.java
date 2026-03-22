package dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks;

import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;

import java.util.Set;

public abstract class DungeonBlock {

    protected Vector3Int pos;

    public DungeonBlock(Vector3Int pos) {
        this.pos = pos;
    }

    public Vector3Int getPos() {
        return pos;
    }

    @Override
    public String toString() {
        return "DungeonBlock{pos=" + pos + "}";
    }

    public abstract boolean canAttachDecorationOnSide(Set<Vector3Int> allPositions);
}
