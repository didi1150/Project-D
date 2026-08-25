package dev.core.game.settings;

import java.util.HashMap;
import java.util.Map;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.game.coords.Point3D;
import dev.core.game.coords.ViewPoint3D;
import dev.core.game.dungeon.BoundingBox;
import dev.core.game.dungeon.proceduralDungeon.AbstractDungeonGenerator;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameSettings {

    private static GameSettings currentSettings;

    private int floor = 1;
    private ViewPoint3D preLobbySpawn;

    private ViewPoint3D selectionSpawn;
    private Map<RPGClassType, Point3D> selectionLocations;
    private Point3D holeCenter;
    private Map<Integer, ViewPoint3D> bossSpawnLocations = new HashMap<>();
    private Map<Integer, String> bossWorlds = new HashMap<>();
    private Map<Integer, ViewPoint3D> bossPlayerSpawnLocations = new HashMap<>();
    private int minPlayers;
    private boolean setupMode;

//    private Dungeon dungeon;
    private String dungeonWorld;
    private BoundingBox lastGeneratedDungeon;
    private AbstractDungeonGenerator lastGenerator;
    
    private String bossWorld;

    private GameSettings() {
        selectionLocations = new HashMap<RPGClassType, Point3D>();
    }

    public void setSelectionLocations(Map<RPGClassType, Point3D> selectionLocations) {
        this.selectionLocations = selectionLocations;
    }

    public Map<Integer, ViewPoint3D> getBossSpawnLocations() {
        return bossSpawnLocations;
    }

    public void setBossSpawnLocations(Map<Integer, ViewPoint3D> bossSpawnLocations) {
        this.bossSpawnLocations = bossSpawnLocations;
    }

    public ViewPoint3D getBossSpawnLocation(int floorLevel) {
        return bossSpawnLocations.get(floorLevel);
    }

    public void setBossSpawnLocation(int floorLevel, ViewPoint3D point3D) {
        bossSpawnLocations.put(floorLevel, point3D);
    }

    public Map<Integer, String> getBossWorlds() {
        return bossWorlds;
    }

    public void setBossWorlds(Map<Integer, String> bossWorlds) {
        this.bossWorlds = bossWorlds;
    }

    public String getBossWorldForFloor(int floorLevel) {
        String world = bossWorlds.get(floorLevel);
        if (world != null && !world.isBlank()) {
            return world;
        }
        return bossWorld;
    }

    public void setBossWorldForFloor(int floorLevel, String world) {
        bossWorlds.put(floorLevel, world);
    }

    public ViewPoint3D getBossPlayerSpawnLocation(int floorLevel) {
        return bossPlayerSpawnLocations.get(floorLevel);
    }

    public void setBossPlayerSpawnLocation(int floorLevel, ViewPoint3D point3D) {
        bossPlayerSpawnLocations.put(floorLevel, point3D);
    }

    public Map<Integer, ViewPoint3D> getBossPlayerSpawnLocations() {
        return bossPlayerSpawnLocations;
    }

    public void setBossPlayerSpawnLocations(Map<Integer, ViewPoint3D> locations) {
        this.bossPlayerSpawnLocations = locations;
    }

    public String getBossWorld() {
        return getBossWorldForFloor(floor);
    }

    public static GameSettings getCurrentSettings() {
        if (currentSettings == null) {
            currentSettings = new GameSettings();
        }
        return currentSettings;
    }

}
