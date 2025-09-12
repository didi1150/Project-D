package dev.core.game.dungeon;

public class TunnelConnection {

    private final DungeonRoom room;
    private final Direction direction;
    private final Point3D connectionPoint;
    private final int roomHeight;
    private final int tunnelHeight;

    public TunnelConnection(DungeonRoom room, Direction direction, Point3D connectionPoint, int tunnelHeight) {
        this.room = room;
        this.direction = direction;
        this.connectionPoint = connectionPoint;
        this.roomHeight = room.getHeight();
        this.tunnelHeight = tunnelHeight;
    }

    public DungeonRoom getRoom() {
        return room;
    }

    public Direction getDirection() {
        return direction;
    }

    public Point3D getConnectionPoint() {
        return connectionPoint;
    }

    public int getRoomHeight() {
        return roomHeight;
    }

    public int getTunnelHeight() {
        return tunnelHeight;
    }

    public boolean needsFillerWalls() {
        return roomHeight > tunnelHeight;
    }

    public int getFillerWallHeight() {
        return Math.max(0, roomHeight - tunnelHeight);
    }

}
