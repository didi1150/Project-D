package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Dungeon {
    private final List<DungeonRoom> rooms;
    private final List<DungeonTunnel> tunnels;
    private final Set<Point3D> allFloorBlocks;
    private final Set<Point3D> allWallBlocks;
    private final Set<Point3D> allRoofBlocks;
    private final Set<Point3D> allAirBlocks;
    private final Set<Point3D> allDecorativeBlocks;
    private final Set<Point3D> endPortal;
    private final Set<Point3D> ceremonyArea;
    private final List<SpawnLocation> allSpawnLocations;
    private final List<DecorationElement> allDecorations;

    public Dungeon(List<DungeonRoom> rooms, List<DungeonTunnel> tunnels) {
        this.rooms = new ArrayList<>(rooms);
        this.tunnels = new ArrayList<>(tunnels);
        this.allFloorBlocks = new HashSet<>();
        this.allWallBlocks = new HashSet<>();
        this.allRoofBlocks = new HashSet<>();
        this.allAirBlocks = new HashSet<>();
        this.allDecorativeBlocks = new HashSet<>();
        this.allSpawnLocations = new ArrayList<>();
        this.allDecorations = new ArrayList<>();
        this.endPortal = new HashSet<>();
        this.ceremonyArea = new HashSet<>();

        // Combine all room blocks
        for (DungeonRoom room : rooms) {
            allFloorBlocks.addAll(room.getFloorBlocks());
            allWallBlocks.addAll(room.getWallBlocks());
            allRoofBlocks.addAll(room.getRoofBlocks());
            allAirBlocks.addAll(room.getAirBlocks());
            allDecorativeBlocks.addAll(room.getDecorativeBlocks());
            allSpawnLocations.addAll(room.getSpawnLocations());
            allDecorations.addAll(room.getDecorations());
        }

        // Combine all tunnel blocks
        for (DungeonTunnel tunnel : tunnels) {
            allFloorBlocks.addAll(tunnel.getFloorBlocks());
            allWallBlocks.addAll(tunnel.getWallBlocks());
            allRoofBlocks.addAll(tunnel.getRoofBlocks());
            allAirBlocks.addAll(tunnel.getAirBlocks());
        }

        rooms.forEach(room -> {
            if (room instanceof EndPortalRoom endPortalRoom) {
                endPortal.addAll(endPortalRoom.getPortalStructure());
                ceremonyArea.addAll(endPortalRoom.getCeremonyArea());
            }
        });
    }

    public List<DungeonRoom> getRooms() {
        return new ArrayList<>(rooms);
    }

    public List<DungeonTunnel> getTunnels() {
        return new ArrayList<>(tunnels);
    }

    public Set<Point3D> getAllFloorBlocks() {
        return new HashSet<>(allFloorBlocks);
    }

    public Set<Point3D> getAllWallBlocks() {
        return new HashSet<>(allWallBlocks);
    }

    public Set<Point3D> getAllRoofBlocks() {
        return new HashSet<>(allRoofBlocks);
    }

    public Set<Point3D> getAllAirBlocks() {
        return new HashSet<>(allAirBlocks);
    }

    public Set<Point3D> getAllDecorativeBlocks() {
        return new HashSet<>(allDecorativeBlocks);
    }

    public List<SpawnLocation> getAllSpawnLocations() {
        return new ArrayList<>(allSpawnLocations);
    }

    public List<DecorationElement> getAllDecorations() {
        return new ArrayList<>(allDecorations);
    }

    public DungeonRoom getStartRoom() {
        return rooms.isEmpty() ? null : rooms.get(0);
    }

    // Utility methods for spawn management
    public List<SpawnLocation> getSpawnLocationsByTier(SpawnTier tier) {
        return allSpawnLocations.stream().filter(spawn -> spawn.getTier() == tier)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<SpawnLocation> getEliteSpawnLocations() {
        return allSpawnLocations.stream().filter(SpawnLocation::isEliteSpawn)
                .collect(java.util.stream.Collectors.toList());
    }

    public Map<SpawnTier, List<SpawnLocation>> getSpawnLocationsByTierMap() {
        Map<SpawnTier, List<SpawnLocation>> tierMap = new EnumMap<>(SpawnTier.class);
        for (SpawnTier tier : SpawnTier.values()) {
            tierMap.put(tier, getSpawnLocationsByTier(tier));
        }
        return tierMap;
    }

    // Utility methods for decorations
    public List<DecorationElement> getDecorationsByType(DecorationType type) {
        return allDecorations.stream().filter(decoration -> decoration.getType() == type)
                .collect(java.util.stream.Collectors.toList());
    }

    public Map<DecorationType, List<DecorationElement>> getDecorationsByTypeMap() {
        Map<DecorationType, List<DecorationElement>> typeMap = new EnumMap<>(DecorationType.class);
        for (DecorationType type : DecorationType.values()) {
            typeMap.put(type, getDecorationsByType(type));
        }
        return typeMap;
    }

    // Statistics methods
    public DungeonStatistics getStatistics() {
        return new DungeonStatistics(this);
    }
}
