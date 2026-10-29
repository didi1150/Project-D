package dev.core.game.settings;

import java.util.*;

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
    public static final String LOCATIONS_BOSSPLAYERSPAWN = "locations.bossplayerspawn";
    public static final String LEGACY_LOCATIONS_BOSSPLAYERSPAWN = "locations.bossplayrespawn";
    public static final String LOCATIONS_REWARDPLAYERSPAWN = "locations.rewardplayerspawn";
    public static final String LOCATIONS_REWARDCHEST = "locations.rewardchest";
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

        Map<Integer, ViewPoint3D> bossSpawns = new HashMap<>();
        ConfigSection bossSpawnSection = configProvider.getSection(LOCATIONS_BOSSSPAWN);
        for (String key : bossSpawnSection.getKeys()) {
            try {
                int floorIndex = Integer.parseInt(key);
                bossSpawns.put(floorIndex, loadViewCoords(bossSpawnSection.getSection(key)));
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

        Map<Integer, ViewPoint3D> bossPlayerSpawns = loadFloorViewLocations(LOCATIONS_BOSSPLAYERSPAWN);
        if (bossPlayerSpawns.isEmpty()) {
            Map<Integer, ViewPoint3D> legacySpawns = loadFloorViewLocations(LEGACY_LOCATIONS_BOSSPLAYERSPAWN);
            if (!legacySpawns.isEmpty()) {
                legacySpawns.forEach(
                        (floor, point) -> writeViewLocation(LOCATIONS_BOSSPLAYERSPAWN + "." + floor, point));
                configProvider.getRoot().set(LEGACY_LOCATIONS_BOSSPLAYERSPAWN, null);
                bossPlayerSpawns = legacySpawns;
            }
        }

        Point3D holeCenter = loadBlockCoords(configProvider.getSection(LOCATIONS_HOLECENTER));
        int minPlayers = configProvider.getRoot().getInt(MINPLAYERS, 1);


        //boss settings
        ConfigSection bossSettings = configProvider.getSection("bosssettings");

        //floor 1
        ConfigSection floor1 = bossSettings.getSection("floor1");
        int heightThreshold50Percent = floor1.getInt("heightthreshold50percent", 1);
        int heightThreshold100Percent = floor1.getInt("heightthreshold100percent", 2);

        Map<Integer, ViewPoint3D> rewardPlayerSpawns = loadFloorViewLocations(LOCATIONS_REWARDPLAYERSPAWN);
        if (rewardPlayerSpawns.isEmpty()) {
            ConfigSection legacySection = floor1.getSection("rewardplayerspawnlocation");
            if (!legacySection.getKeys().isEmpty()) {
                ViewPoint3D legacy = loadViewCoords(legacySection);
                rewardPlayerSpawns.put(1, legacy);
                writeViewLocation(LOCATIONS_REWARDPLAYERSPAWN + ".1", legacy);
                floor1.set("rewardplayerspawnlocation", null);
            }
        }

        Map<Integer, Point3D> rewardChests = loadFloorBlockLocations(LOCATIONS_REWARDCHEST);
        if (rewardChests.isEmpty()) {
            ConfigSection legacySection = floor1.getSection("rewardchestlocation");
            if (!legacySection.getKeys().isEmpty()) {
                Point3D legacy = loadBlockCoords(legacySection);
                rewardChests.put(1, legacy);
                writeBlockLocation(LOCATIONS_REWARDCHEST + ".1", legacy);
                floor1.set("rewardchestlocation", null);
            }
        }

        configProvider.save();

        gameSettings.setMinPlayers(minPlayers);
        gameSettings.setDungeonWorld(dungeonWorld);
        gameSettings.setBossWorld(bossWorld);
        gameSettings.setBossWorlds(bossWorlds);
        gameSettings.setSelectionSpawn(selectionSpawn);
        gameSettings.setPreLobbySpawn(preLobbySpawn);
        gameSettings.setHoleCenter(holeCenter);
        gameSettings.setBossSpawnLocations(bossSpawns);
        gameSettings.setBossPlayerSpawnLocations(bossPlayerSpawns);
        gameSettings.setRewardPlayerSpawnLocations(rewardPlayerSpawns);
        gameSettings.setRewardChestLocations(rewardChests);
        gameSettings.setSelectionLocations(selectionClasses);
        gameSettings.setSetupMode(setupMode);

        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(1);
        floor1BossSettings.setHeightThreshold50Percent(heightThreshold50Percent);
        floor1BossSettings.setHeightThreshold100Percent(heightThreshold100Percent);
    }

    public boolean toggleSetup() {
        boolean currentSetup = gameSettings.isSetupMode();
        gameSettings.setSetupMode(!currentSetup);
        configProvider.getRoot().set("setup", !currentSetup);
        configProvider.save();
        return !currentSetup;
    }

    public void deleteLocation(String path) {
        String parentPath = path.substring(0, path.lastIndexOf("."));
        String childString = path.substring(path.lastIndexOf(".") + 1);
        ConfigSection section = configProvider.getSection(parentPath);
        section.set(childString, null);
//        section.getSection(childString);
        configProvider.save();
        load();
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
        load(); //TODO tmp fix
    }

    public void setLocation(String path, Point3D point3D) {
        ConfigSection section = configProvider.getSection(path);
        section.set("x", point3D.getX());
        section.set("y", point3D.getY());
        section.set("z", point3D.getZ());
        section.set("world", point3D.getWorld());
        configProvider.save();
        load(); //TODO tmp fix
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

    private Map<Integer, ViewPoint3D> loadFloorViewLocations(String path) {
        Map<Integer, ViewPoint3D> locations = new HashMap<>();
        ConfigSection section = configProvider.getSection(path);
        for (String key : section.getKeys()) {
            try {
                locations.put(Integer.parseInt(key), loadViewCoords(section.getSection(key)));
            } catch (NumberFormatException ignored) {
            }
        }
        return locations;
    }

    private Map<Integer, Point3D> loadFloorBlockLocations(String path) {
        Map<Integer, Point3D> locations = new HashMap<>();
        ConfigSection section = configProvider.getSection(path);
        for (String key : section.getKeys()) {
            try {
                locations.put(Integer.parseInt(key), loadBlockCoords(section.getSection(key)));
            } catch (NumberFormatException ignored) {
            }
        }
        return locations;
    }

    private void writeViewLocation(String path, ViewPoint3D viewPoint3D) {
        ConfigSection section = configProvider.getSection(path);
        section.set("x", viewPoint3D.getX());
        section.set("y", viewPoint3D.getY());
        section.set("z", viewPoint3D.getZ());
        section.set("yaw", viewPoint3D.getYaw());
        section.set("pitch", viewPoint3D.getPitch());
        section.set("world", viewPoint3D.getWorld());
    }

    private void writeBlockLocation(String path, Point3D point3D) {
        ConfigSection section = configProvider.getSection(path);
        section.set("x", point3D.getX());
        section.set("y", point3D.getY());
        section.set("z", point3D.getZ());
        section.set("world", point3D.getWorld());
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

    public void setClassBlockLocation(RPGClassType classType, Point3D point3D) {
        gameSettings.getSelectionLocations().put(classType, point3D);
        ConfigSection section = configProvider.getSection(LOCATIONS_SELECTIONCLASSES + "." + classType);
        section.set("x", point3D.getX());
        section.set("y", point3D.getY());
        section.set("z", point3D.getZ());
        section.set("world", point3D.getWorld());
        configProvider.save();
    }

    public void setBossSpawnLocation(int floorLevel, ViewPoint3D viewPoint3D) {
        gameSettings.setBossSpawnLocation(floorLevel, viewPoint3D);
        ConfigSection section = configProvider.getSection(LOCATIONS_BOSSSPAWN + "." + floorLevel);
        section.set("x", viewPoint3D.getX());
        section.set("y", viewPoint3D.getY());
        section.set("z", viewPoint3D.getZ());
        section.set("yaw", viewPoint3D.getYaw());
        section.set("pitch", viewPoint3D.getPitch());
        section.set("world", viewPoint3D.getWorld());
        configProvider.save();
    }

    public void setBossPlayerSpawnLocation(int floorLevel, ViewPoint3D viewPoint3D) {
        gameSettings.setBossPlayerSpawnLocation(floorLevel, viewPoint3D);
        ConfigSection section = configProvider.getSection(LOCATIONS_BOSSPLAYERSPAWN + "." + floorLevel);
        section.set("x", viewPoint3D.getX());
        section.set("y", viewPoint3D.getY());
        section.set("z", viewPoint3D.getZ());
        section.set("yaw", viewPoint3D.getYaw());
        section.set("pitch", viewPoint3D.getPitch());
        section.set("world", viewPoint3D.getWorld());
        configProvider.save();
    }

    public void setMinPlayers(int count) {
        configProvider.getRoot().set(MINPLAYERS, count);
        configProvider.save();
    }

    public void setHeightThreshold50Percent(int floorLevel, int height) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        floor1BossSettings.setHeightThreshold50Percent(height);
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel);
        section.set("heightthreshold50percent", height);
        configProvider.save();
    }

    public void setHeightThreshold100Percent(int floorLevel, int height) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        floor1BossSettings.setHeightThreshold50Percent(height);
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel);
        section.set("heightthreshold100percent", height);
        configProvider.save();
    }

    public void setRewardPlayerSpawnLocation(int floorLevel, ViewPoint3D viewPoint3D) {
        gameSettings.setRewardPlayerSpawnLocation(floorLevel, viewPoint3D);
        writeViewLocation(LOCATIONS_REWARDPLAYERSPAWN + "." + floorLevel, viewPoint3D);
        configProvider.save();
    }

    public boolean removeRewardPlayerSpawnLocation(int floorLevel) {
        if (gameSettings.getRewardPlayerSpawnLocation(floorLevel) == null) {
            return false;
        }
        gameSettings.getRewardPlayerSpawnLocations().remove(floorLevel);
        configProvider.getSection(LOCATIONS_REWARDPLAYERSPAWN).set(String.valueOf(floorLevel), null);
        configProvider.save();
        return true;
    }

    public void setRewardChestLocation(int floorLevel, Point3D point3D) {
        gameSettings.setRewardChestLocation(floorLevel, point3D);
        writeBlockLocation(LOCATIONS_REWARDCHEST + "." + floorLevel, point3D);
        configProvider.save();
    }

    public boolean removeRewardChestLocation(int floorLevel) {
        if (gameSettings.getRewardChestLocation(floorLevel) == null) {
            return false;
        }
        gameSettings.getRewardChestLocations().remove(floorLevel);
        configProvider.getSection(LOCATIONS_REWARDCHEST).set(String.valueOf(floorLevel), null);
        configProvider.save();
        return true;
    }
}
