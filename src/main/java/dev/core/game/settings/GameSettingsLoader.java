package dev.core.game.settings;

import java.util.HashMap;
import java.util.Map;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.game.coords.Point3D;
import dev.core.game.coords.ViewPoint3D;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

public class GameSettingsLoader {

    public static final String LOCATIONS_HOLECENTER = "locations.holecenter";
    public static final String LOCATIONS_PRELOBBYSPAWN = "locations.prelobbyspawn";
    public static final String LOCATIONS_SELECTIONSPAWN = "locations.selectionspawn";
    public static final String LOCATIONS_SELECTIONCLASSES = "locations.selectionclasses";
    public static final String LOCATIONS_BOSSSPAWN = "locations.bossspawn";
    public static final String MINPLAYERS = "minplayers";
    private GameSettings gameSettings;
    private ConfigProvider configProvider;

    public GameSettingsLoader(GameSettings gameSettings, ConfigProvider configProvider) {
        this.gameSettings = gameSettings;
        this.configProvider = configProvider;
    }

    public void load() {
        boolean setupMode = configProvider.getRoot().getBoolean("setup", true);
        String dungeonWorld = configProvider.getRoot().getString("dungeonworld", "");
        String bossWorld = configProvider.getRoot().getString("bossworld", "");
        ViewPoint3D preLobbySpawn = loadViewCoords(configProvider.getSection(LOCATIONS_PRELOBBYSPAWN));
        ViewPoint3D selectionSpawn = loadViewCoords(configProvider.getSection(LOCATIONS_SELECTIONSPAWN));
        Map<RPGClassType, Point3D> selectionClasses = new HashMap<>();
        ConfigSection selectionClassesSection = configProvider.getSection(LOCATIONS_SELECTIONCLASSES);
        for (String key : selectionClassesSection.getKeys()) {
            RPGClassType classType = RPGClassType.valueOf(key);
            Point3D coord = loadBlockCoords(selectionClassesSection.getSection(key));
            selectionClasses.put(classType, coord);
        }

        Map<Integer, Point3D> bossSpawns = new HashMap<>();
        ConfigSection bossSpawnSection = configProvider.getSection(LOCATIONS_BOSSSPAWN);
        for (String key : bossSpawnSection.getKeys()) {
            try {
                int floorIndex = Integer.parseInt(key);
                bossSpawns.put(floorIndex, loadBlockCoords(bossSpawnSection.getSection(key)));
            } catch (NumberFormatException ignored) {
            }
        }

        Map<Integer, String> bossWorlds = new HashMap<>();
        ConfigSection bossWorldsSection = configProvider.getSection("bossworlds");
        for (String key : bossWorldsSection.getKeys()) {
            try {
                int floorIndex = Integer.parseInt(key.replaceAll("[^0-9]", ""));
                bossWorlds.put(floorIndex, bossWorldsSection.getString(key, ""));
            } catch (NumberFormatException ignored) {
            }
        }

        Map<Integer, Point3D> bossPlayerSpawns = new HashMap<>();
        ConfigSection bossPlayerSpawnSection = configProvider.getSection("locations.bossplayrespawn");
        for (String key : bossPlayerSpawnSection.getKeys()) {
            try {
                int floorIndex = Integer.parseInt(key);
                bossPlayerSpawns.put(floorIndex, loadBlockCoords(bossPlayerSpawnSection.getSection(key)));
            } catch (NumberFormatException ignored) {
            }
        }

        Point3D holeCenter = loadBlockCoords(configProvider.getSection(LOCATIONS_HOLECENTER));
        int minPlayers = configProvider.getRoot().getInt(MINPLAYERS, 1);

        gameSettings.setMinPlayers(minPlayers);
        gameSettings.setDungeonWorld(dungeonWorld);
        gameSettings.setBossWorld(bossWorld);
        gameSettings.setBossWorlds(bossWorlds);
        gameSettings.setSelectionSpawn(selectionSpawn);
        gameSettings.setPreLobbySpawn(preLobbySpawn);
        gameSettings.setHoleCenter(holeCenter);
        gameSettings.setBossSpawnLocations(bossSpawns);
        gameSettings.setBossPlayerSpawnLocations(bossPlayerSpawns);
        gameSettings.setSelectionLocations(selectionClasses);
        gameSettings.setSetupMode(setupMode);
    }

    public boolean toggleSetup() {
        boolean currentSetup = gameSettings.isSetupMode();
        gameSettings.setSetupMode(!currentSetup);
        configProvider.getRoot().set("setup", !currentSetup);
        configProvider.save();
        return !currentSetup;
    }

    public void setViewLocation(String path, ViewPoint3D viewPoint3D) {
        ConfigSection section = configProvider.getSection(path);
        section.set("x", viewPoint3D.getX());
        section.set("y", viewPoint3D.getY());
        section.set("z", viewPoint3D.getZ());
        section.set("yaw", viewPoint3D.getYaw());
        section.set("pitch", viewPoint3D.getPitch());
        section.set("world", viewPoint3D.getWorld());
        configProvider.save();
    }

    public void setLocation(String path, Point3D point3D) {
        ConfigSection section = configProvider.getSection(path);
        section.set("x", point3D.getX());
        section.set("y", point3D.getY());
        section.set("z", point3D.getZ());
        section.set("world", point3D.getWorld());
        configProvider.save();
    }

    private Point3D loadBlockCoords(ConfigSection configSection) {
        String world = configSection.getString("world", null);
        int x = configSection.getInt("x", 0);
        int y = configSection.getInt("y", 0);
        int z = configSection.getInt("z", 0);

        return new Point3D(x, y, z, world);
    }

    private ViewPoint3D loadViewCoords(ConfigSection configSection) {
        String world = configSection.getString("world", null);
        int x = configSection.getInt("x", 0);
        int y = configSection.getInt("y", 0);
        int z = configSection.getInt("z", 0);
        float yaw = (float) configSection.getDouble("yaw", 0);
        float pitch = (float) configSection.getDouble("pitch", 0);

        return new ViewPoint3D(x, y, z, world, yaw, pitch);
    }

    public void setDungeonWorld(String world) {
        gameSettings.setDungeonWorld(world);
        configProvider.getRoot().set("dungeonworld", world);
        configProvider.save();
    }

    public void setBossWorld(String world, int floor) {
        gameSettings.setBossWorldForFloor(floor, world);
        configProvider.getSection("bossworlds").set("Floor" + floor, world);
        configProvider.save();
    }

    public void setBossSpawnLocation(int floorLevel, Point3D point3D) {
        gameSettings.setBossSpawnLocation(floorLevel, point3D);
        ConfigSection section = configProvider.getSection(LOCATIONS_BOSSSPAWN + "." + floorLevel);
        section.set("x", point3D.getX());
        section.set("y", point3D.getY());
        section.set("z", point3D.getZ());
        configProvider.save();
    }

    public void setBossPlayerSpawnLocation(int floorLevel, Point3D point3D) {
        gameSettings.setBossPlayerSpawnLocation(floorLevel, point3D);
        ConfigSection section = configProvider.getSection("locations.bossplayrespawn." + floorLevel);
        section.set("x", point3D.getX());
        section.set("y", point3D.getY());
        section.set("z", point3D.getZ());
        configProvider.save();
    }

    public void setMinPlayers(int count) {
        configProvider.getRoot().set(MINPLAYERS, count);
        configProvider.save();
    }
}
