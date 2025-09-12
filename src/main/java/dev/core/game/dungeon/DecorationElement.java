package dev.core.game.dungeon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DecorationElement {

    private final String id;
    private final DecorationType type;
    private final Point3D centerPosition;
    private final Set<Point3D> occupiedPositions;
    private final Map<Point3D, String> blockTypes; // Position -> Block identifier
    private final int size;

    public DecorationElement(String id, DecorationType type, Point3D centerPosition, int size) {
        this.id = id;
        this.type = type;
        this.centerPosition = centerPosition;
        this.size = size;
        this.occupiedPositions = new HashSet<>();
        this.blockTypes = new HashMap<>();
    }

    public void addBlock(Point3D position, String blockType) {
        occupiedPositions.add(position);
        blockTypes.put(position, blockType);
    }

    public String getId() {
        return id;
    }

    public DecorationType getType() {
        return type;
    }

    public Point3D getCenterPosition() {
        return centerPosition;
    }

    public Set<Point3D> getOccupiedPositions() {
        return new HashSet<>(occupiedPositions);
    }

    public Map<Point3D, String> getBlockTypes() {
        return new HashMap<>(blockTypes);
    }

    public int getSize() {
        return size;
    }

    public String getBlockType(Point3D position) {
        return blockTypes.get(position);
    }

}
