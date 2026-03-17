package dev.core.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.BoundingBox;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

public abstract class AbstractDungeonGenerator {

    protected record Pair<K,V> (K first, V second){}

    protected Set<Vector3Int> floorPositions = new LinkedHashSet<>();
    protected Set<Vector3Int> wallPositions = new LinkedHashSet<>();

    protected Vector3Int startPosition = Vector3Int.ZERO;

    public AbstractDungeonGenerator(Vector3Int startPosition) {
        this.startPosition = startPosition;
    }

    public AbstractDungeonGenerator() {
    }

    public Set<Vector3Int> getFloorPositions() {
        return floorPositions;
    }

    public Set<Vector3Int> getWallPositions() {
        return wallPositions;
    }

    public void setStartPosition(Vector3Int startPosition) {
        this.startPosition = startPosition;
    }

    public abstract BoundingBox getMaxBounds();

    public void generateDungeon()
    {
        generateDungeon(System.currentTimeMillis());
    }

    public void generateDungeon(long seed)
    {
        System.out.println("Generating Dungeon with seed: " + seed);
        long time = System.currentTimeMillis();
        runProceduralGeneration(new Random(seed));
        System.out.println("Dungeon Generation took " + ((System.currentTimeMillis() - time) / 1000d) + " secs");
    }

    protected abstract void runProceduralGeneration(Random random);

}
