package dev.core.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.BoundingBox;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

public abstract class AbstractDungeonGenerator {

    protected record Pair<K,V> (K first, V second){}
    protected record Triplet<A,B,C> (A first, B second, C third){}

    protected Set<Vector3Int> floorPositions = new LinkedHashSet<>();
    protected Set<Vector3Int> wallPositions = new LinkedHashSet<>();

    protected Vector3Int startPosition = Vector3Int.ZERO;

    protected long lastUsedSeed;

    public AbstractDungeonGenerator(Vector3Int startPosition) {
        this.startPosition = startPosition;
    }

    public AbstractDungeonGenerator() {
    }

    public Set<Vector3Int> getFloorPositions() {
        return new LinkedHashSet<>(floorPositions);
    }

    public Set<Vector3Int> getWallPositions() {
        return new LinkedHashSet<>(wallPositions);
    }

    public void setStartPosition(Vector3Int startPosition) {
        this.startPosition = startPosition;
    }

    public long getLastUsedSeed() {
        return lastUsedSeed;
    }

    public abstract BoundingBox getMaxBounds();

    public void generateDungeon()
    {
        generateDungeon(System.currentTimeMillis());
    }

    public void generateDungeon(long seed)
    {
        lastUsedSeed = seed;
        System.out.println("Generating Dungeon with seed: " + seed);
        long time = System.currentTimeMillis();
        runProceduralGeneration(new Random(seed));
        System.out.println("Dungeon Generation took " + ((System.currentTimeMillis() - time) / 1000d) + " secs");
    }

    protected abstract void runProceduralGeneration(Random random);

}
