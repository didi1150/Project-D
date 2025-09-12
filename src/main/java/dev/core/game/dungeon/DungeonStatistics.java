package dev.core.game.dungeon;

import java.util.EnumMap;
import java.util.Map;

public class DungeonStatistics {

    private final int totalRooms;
    private final int totalTunnels;
    private final int totalSpawnLocations;
    private final int totalDecorations;
    private final Map<SpawnTier, Integer> spawnTierCounts;
    private final Map<DecorationType, Integer> decorationTypeCounts;
    private final Map<RoomType, Integer> roomTypeCounts;
    private final int averageRoomSize;
    private final int largestRoomSize;
    private final int smallestRoomSize;

    public DungeonStatistics(Dungeon dungeon) {
        this.totalRooms = dungeon.getRooms().size();
        this.totalTunnels = dungeon.getTunnels().size();
        this.totalSpawnLocations = dungeon.getAllSpawnLocations().size();
        this.totalDecorations = dungeon.getAllDecorations().size();

        // Calculate spawn tier distribution
        this.spawnTierCounts = new EnumMap<>(SpawnTier.class);
        for (SpawnTier tier : SpawnTier.values()) {
            spawnTierCounts.put(tier, dungeon.getSpawnLocationsByTier(tier).size());
        }

        // Calculate decoration type distribution
        this.decorationTypeCounts = new EnumMap<>(DecorationType.class);
        for (DecorationType type : DecorationType.values()) {
            decorationTypeCounts.put(type, dungeon.getDecorationsByType(type).size());
        }

        // Calculate room statistics
        this.roomTypeCounts = new EnumMap<>(RoomType.class);
        int totalSize = 0;
        int maxSize = 0;
        int minSize = Integer.MAX_VALUE;

        for (DungeonRoom room : dungeon.getRooms()) {
            RoomType type = room.getType();
            roomTypeCounts.put(type, roomTypeCounts.getOrDefault(type, 0) + 1);

            int size = room.getSize();
            totalSize += size;
            maxSize = Math.max(maxSize, size);
            minSize = Math.min(minSize, size);
        }

        this.averageRoomSize = totalRooms > 0 ? totalSize / totalRooms : 0;
        this.largestRoomSize = maxSize;
        this.smallestRoomSize = minSize == Integer.MAX_VALUE ? 0 : minSize;
    }

    // Getters
    public int getTotalRooms() {
        return totalRooms;
    }

    public int getTotalTunnels() {
        return totalTunnels;
    }

    public int getTotalSpawnLocations() {
        return totalSpawnLocations;
    }

    public int getTotalDecorations() {
        return totalDecorations;
    }

    public Map<SpawnTier, Integer> getSpawnTierCounts() {
        return new EnumMap<>(spawnTierCounts);
    }

    public Map<DecorationType, Integer> getDecorationTypeCounts() {
        return new EnumMap<>(decorationTypeCounts);
    }

    public Map<RoomType, Integer> getRoomTypeCounts() {
        return new EnumMap<>(roomTypeCounts);
    }

    public int getAverageRoomSize() {
        return averageRoomSize;
    }

    public int getLargestRoomSize() {
        return largestRoomSize;
    }

    public int getSmallestRoomSize() {
        return smallestRoomSize;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Dungeon Statistics ===\n");
        sb.append("Rooms: ").append(totalRooms).append("\n");
        sb.append("Tunnels: ").append(totalTunnels).append("\n");
        sb.append("Spawn Locations: ").append(totalSpawnLocations).append("\n");
        sb.append("Decorations: ").append(totalDecorations).append("\n");
        sb.append("Average Room Size: ").append(averageRoomSize).append("\n");
        sb.append("Size Range: ").append(smallestRoomSize).append("-").append(largestRoomSize).append("\n");

        sb.append("\n--- Spawn Tier Distribution ---\n");
        for (Map.Entry<SpawnTier, Integer> entry : spawnTierCounts.entrySet()) {
            if (entry.getValue() > 0) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        sb.append("\n--- Room Type Distribution ---\n");
        for (Map.Entry<RoomType, Integer> entry : roomTypeCounts.entrySet()) {
            if (entry.getValue() > 0) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        return sb.toString();
    }

}
