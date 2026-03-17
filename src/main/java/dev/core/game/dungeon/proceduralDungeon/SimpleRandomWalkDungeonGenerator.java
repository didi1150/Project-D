package dev.core.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.BoundingBox;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

public class SimpleRandomWalkDungeonGenerator extends AbstractDungeonGenerator {

    public record SimpleRandomWalkParameters(int iterations, int walkLength, boolean startRandomlyEachIteration) {}

    protected int iterations = 50;
    protected int walkLength = 15;
    protected boolean startRandomlyEachIteration = true;

    public SimpleRandomWalkDungeonGenerator(Vector3Int startPosition, int iterations, int walkLength, boolean startRandomlyEachIteration) {
        super(startPosition);
        this.iterations = iterations;
        this.walkLength = walkLength;
        this.startRandomlyEachIteration = startRandomlyEachIteration;
    }

    public SimpleRandomWalkDungeonGenerator(int iterations, int walkLength, boolean startRandomlyEachIteration) {
        this.iterations = iterations;
        this.walkLength = walkLength;
        this.startRandomlyEachIteration = startRandomlyEachIteration;
    }

    public SimpleRandomWalkDungeonGenerator(Vector3Int startPosition, SimpleRandomWalkParameters randomWalkParameters) {
        super(startPosition);
        this.iterations = randomWalkParameters.iterations;
        this.walkLength = randomWalkParameters.walkLength;
        this.startRandomlyEachIteration = randomWalkParameters.startRandomlyEachIteration;
    }

    public SimpleRandomWalkDungeonGenerator(SimpleRandomWalkParameters randomWalkParameters) {
        this.iterations = randomWalkParameters.iterations;
        this.walkLength = randomWalkParameters.walkLength;
        this.startRandomlyEachIteration = randomWalkParameters.startRandomlyEachIteration;
    }

    public SimpleRandomWalkDungeonGenerator() {
    }

    @Override
    public BoundingBox getMaxBounds() {
        return new BoundingBox(startPosition, startPosition.add(walkLength, 0, walkLength));
    }

    @Override
    protected void runProceduralGeneration(Random random) {
        floorPositions = runRandomWalk(new SimpleRandomWalkParameters(iterations, walkLength, startRandomlyEachIteration), startPosition, random);
        WallGenerator.createWalls(floorPositions);
    }

    protected Set<Vector3Int> runRandomWalk(SimpleRandomWalkParameters parameters, Vector3Int position, Random random)
    {
        var currentPosition = position;
        Set<Vector3Int> floorPositions = new LinkedHashSet<>();
        for (int i = 0; i < parameters.iterations; i++)
        {
            var path = ProceduralGenerationAlgorithms.simpleRandomWalk(currentPosition, parameters.walkLength, random);
            floorPositions.addAll(path);
            if (parameters.startRandomlyEachIteration)
                currentPosition = getRandomElement(floorPositions, random);
        }
        return floorPositions;
    }

    protected <E> E getRandomElement(Set<E> set, Random random) {
        int counter = random.nextInt(0, set.size());
        for (var e : set) {
            if (counter == 0) return e;
            counter--;
        }
        return null;
    }
}
