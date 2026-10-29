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
    private Map<Integer, ViewPoint3D> rewardPlayerSpawnLocations = new HashMap<>();
    private Map<Integer, Point3D> rewardChestLocations = new HashMap<>();
    private int minPlayers;
    private boolean setupMode;

//    private Dungeon dungeon;
    private String dungeonWorld;
    private BoundingBox lastGeneratedDungeon;
    private AbstractDungeonGenerator lastGenerator;
    
    private String bossWorld;

    private Map<Integer, BossSettings> bossSettings = new HashMap<>();

    private GameSettings() {
        selectionLocations = new HashMap<RPGClassType, Point3D>();
        bossSettings.put(1, new Floor1BossSettings(1));
    }

    public BossSettings getBossSettings(int floor) {
        return bossSettings.get(floor);
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

    public Map<Integer, ViewPoint3D> getRewardPlayerSpawnLocations() {
        return rewardPlayerSpawnLocations;
    }

    public void setRewardPlayerSpawnLocations(Map<Integer, ViewPoint3D> locations) {
        this.rewardPlayerSpawnLocations = locations;
    }

    public ViewPoint3D getRewardPlayerSpawnLocation(int floorLevel) {
        return rewardPlayerSpawnLocations.get(floorLevel);
    }

    public void setRewardPlayerSpawnLocation(int floorLevel, ViewPoint3D point3D) {
        rewardPlayerSpawnLocations.put(floorLevel, point3D);
    }

    public Map<Integer, Point3D> getRewardChestLocations() {
        return rewardChestLocations;
    }

    public void setRewardChestLocations(Map<Integer, Point3D> locations) {
        this.rewardChestLocations = locations;
    }

    public Point3D getRewardChestLocation(int floorLevel) {
        return rewardChestLocations.get(floorLevel);
    }

    public void setRewardChestLocation(int floorLevel, Point3D point3D) {
        rewardChestLocations.put(floorLevel, point3D);
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
