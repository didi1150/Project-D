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
    public static final String LOCATIONS_BOSSPLAYERSPAWN = "locations.bossplayrespawn";
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

        Map<Integer, ViewPoint3D> bossPlayerSpawns = new HashMap<>();
        ConfigSection bossPlayerSpawnSection = configProvider.getSection("locations.bossplayrespawn");
        for (String key : bossPlayerSpawnSection.getKeys()) {
            try {
                int floorIndex = Integer.parseInt(key);
                bossPlayerSpawns.put(floorIndex, loadViewCoords(bossPlayerSpawnSection.getSection(key)));
            } catch (NumberFormatException ignored) {
            }
        }

        Point3D holeCenter = loadBlockCoords(configProvider.getSection(LOCATIONS_HOLECENTER));
        int minPlayers = configProvider.getRoot().getInt(MINPLAYERS, 1);


        //boss settings
        ConfigSection bossSettings = configProvider.getSection("bosssettings");

        //floor 1
        ConfigSection floor1 = bossSettings.getSection("floor1");
        Map<Integer, Point3D> pillarLocations = new HashMap<>();
        for (String key : floor1.getSection("pillarlocations").getKeys()) {
            try {
                int pillar = Integer.parseInt(key);
                pillarLocations.put(pillar, loadBlockCoords(floor1.getSection("pillarlocations").getSection(key)));
            } catch (NumberFormatException ignored) {
            }
        }
        Map<Integer, Point3D> chainLinkLocations = new HashMap<>();
        for (String key : floor1.getSection("chainlinklocations").getKeys()) {
            try {
                int chainlink = Integer.parseInt(key);
                chainLinkLocations.put(chainlink, loadBlockCoords(floor1.getSection("chainlinklocations").getSection(key)));
            } catch (NumberFormatException ignored) {
            }
        }
        int heightThreshold50Percent = floor1.getInt("heightthreshold50percent", 1);
        int heightThreshold100Percent = floor1.getInt("heightthreshold100percent", 2);
        Set<Point3D> crateSpawnerLocations = new HashSet<>();
        for (ConfigSection section : floor1.getSectionList("cratespawnerlocations")) {
            crateSpawnerLocations.add(loadBlockCoords(section));
        }
        Set<Point3D> stalactiteLocations = new HashSet<>();
        for (ConfigSection section : floor1.getSectionList("stalactitelocations")) {
            stalactiteLocations.add(loadBlockCoords(section));
        }
        Set<Point3D> mobWaveSpawnLocations = new HashSet<>();
        for (ConfigSection section : floor1.getSectionList("mobwavespawnlocations")) {
            mobWaveSpawnLocations.add(loadBlockCoords(section));
        }
        ViewPoint3D rewardPlayerSpawnLocation = loadViewCoords(floor1.getSection("rewardplayerspawnlocation"));
        if (floor1.getSection("rewardplayerspawnlocation").getKeys().isEmpty())
            rewardPlayerSpawnLocation = null;
        ViewPoint3D rewardChestLocation = loadViewCoords(floor1.getSection("rewardchestlocation"));
        if (floor1.getSection("rewardchestlocation").getKeys().isEmpty())
            rewardChestLocation = null;

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
        gameSettings.setSelectionLocations(selectionClasses);
        gameSettings.setSetupMode(setupMode);

        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(1);
        floor1BossSettings.setPillarLocations(pillarLocations);
        floor1BossSettings.setChainLinkLocations(chainLinkLocations);
        floor1BossSettings.setHeightThreshold50Percent(heightThreshold50Percent);
        floor1BossSettings.setHeightThreshold100Percent(heightThreshold100Percent);
        floor1BossSettings.setCrateSpawnerLocations(crateSpawnerLocations);
        floor1BossSettings.setStalactiteLocations(stalactiteLocations);
        floor1BossSettings.setMobWaveSpawnLocations(mobWaveSpawnLocations);
        floor1BossSettings.setRewardPlayerSpawnLocation(rewardPlayerSpawnLocation);
        floor1BossSettings.setRewardChestLocation(rewardChestLocation);
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

    public void setPillarLocation(int floorLevel, int pillar, Point3D point3D) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        floor1BossSettings.getPillarLocations().put(pillar, point3D);
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel + ".pillarlocations." + pillar);
        section.set("x", point3D.getX());
        section.set("y", point3D.getY());
        section.set("z", point3D.getZ());
        section.set("world", point3D.getWorld());
        configProvider.save();
    }

    public boolean removePillarLocation(int floorLevel, Point3D pos) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        if (!floor1BossSettings.getPillarLocations().containsValue(pos)) {
            return false;
        }
        int pillar = floor1BossSettings.getPillarLocations().keySet().stream().filter(p -> floor1BossSettings.getPillarLocations().get(p).equals(pos)).findFirst().orElse(0);
        floor1BossSettings.getPillarLocations().remove(pillar);
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel);
        section.set("pillarlocations", null);
        floor1BossSettings.getPillarLocations().forEach((num, point3D) -> {
            ConfigSection sec = section.getSection("pillarlocations." + num);
            sec.set("x", point3D.getX());
            sec.set("y", point3D.getY());
            sec.set("z", point3D.getZ());
            sec.set("world", point3D.getWorld());
        });
        configProvider.save();
        return true;
    }

    public void setChainLinkLocation(int floorLevel, int chainlink, Point3D point3D) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        floor1BossSettings.getChainLinkLocations().put(chainlink, point3D);
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel + ".chainlinklocations." + chainlink);
        section.set("x", point3D.getX());
        section.set("y", point3D.getY());
        section.set("z", point3D.getZ());
        section.set("world", point3D.getWorld());
        configProvider.save();
    }

    public boolean removeChainLinkLocation(int floorLevel, Point3D pos) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        if (!floor1BossSettings.getChainLinkLocations().containsValue(pos)) {
            return false;
        }
        int chainlink = floor1BossSettings.getChainLinkLocations().keySet().stream().filter(p -> floor1BossSettings.getChainLinkLocations().get(p).equals(pos)).findFirst().orElse(0);
        floor1BossSettings.getChainLinkLocations().remove(chainlink);
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel);
        section.set("chainlinklocations", null);
        floor1BossSettings.getChainLinkLocations().forEach((num, point3D) -> {
            ConfigSection sec = section.getSection("chainlinklocations." + num);
            sec.set("x", point3D.getX());
            sec.set("y", point3D.getY());
            sec.set("z", point3D.getZ());
            sec.set("world", point3D.getWorld());
        });
        configProvider.save();
        return true;
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

    public void addCrateSpawnerLocation(int floorLevel, Point3D point3D) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        floor1BossSettings.getCrateSpawnerLocations().add(point3D);
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel);
        updateList(section, "cratespawnerlocations", floor1BossSettings.getCrateSpawnerLocations());
    }

    public void addStalactiteLocation(int floorLevel, Point3D point3D) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        floor1BossSettings.getStalactiteLocations().add(point3D);
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel);
        updateList(section, "stalactitelocations", floor1BossSettings.getStalactiteLocations());
    }

    public void addMobWaveSpawnLocation(int floorLevel, Point3D point3D) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        floor1BossSettings.getMobWaveSpawnLocations().add(point3D);
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel);
        updateList(section, "mobwavespawnlocations", floor1BossSettings.getMobWaveSpawnLocations());
    }

    public void updateList(ConfigSection section, String path, Set<Point3D> set) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Point3D p : set) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("x", p.getX());
            m.put("y", p.getY());
            m.put("z", p.getZ());
            m.put("world", p.getWorld());
            list.add(m);
        }
        section.set(path, list);
        configProvider.save();
    }

    public boolean removeCrateSpawnerLocation(int floorLevel, Point3D point3D) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        if (!floor1BossSettings.getCrateSpawnerLocations().remove(point3D)) {
            return false;
        }
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel);
        updateList(section, "cratespawnerlocations", floor1BossSettings.getCrateSpawnerLocations());
        return true;
    }

    public boolean removeStalactiteLocation(int floorLevel, Point3D point3D) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        if (!floor1BossSettings.getStalactiteLocations().remove(point3D)) {
            return false;
        }
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel);
        updateList(section, "stalactitelocations", floor1BossSettings.getStalactiteLocations());
        return true;
    }

    public boolean removeMobWaveSpawnLocation(int floorLevel, Point3D point3D) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        if (!floor1BossSettings.getMobWaveSpawnLocations().remove(point3D)) {
            return false;
        }
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel);
        updateList(section, "mobwavespawnlocations", floor1BossSettings.getMobWaveSpawnLocations());
        return true;
    }

    public void setRewardPlayerSpawnLocation(int floorLevel, ViewPoint3D viewPoint3D) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        floor1BossSettings.setRewardPlayerSpawnLocation(viewPoint3D);
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel + ".rewardplayerspawnlocation");
        section.set("x", viewPoint3D.getX());
        section.set("y", viewPoint3D.getY());
        section.set("z", viewPoint3D.getZ());
        section.set("yaw", viewPoint3D.getYaw());
        section.set("pitch", viewPoint3D.getPitch());
        section.set("world", viewPoint3D.getWorld());
        configProvider.save();
    }

    public boolean removeRewardPlayerSpawnLocation(int floorLevel) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        if (floor1BossSettings.getRewardPlayerSpawnLocation() == null) {
            return false;
        }
        floor1BossSettings.setRewardPlayerSpawnLocation(null);
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel);
        section.set("rewardplayerspawnlocation", null);
        return true;
    }

    public void setRewardChestLocation(int floorLevel, Point3D point3D) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        floor1BossSettings.setRewardChestLocation(point3D);
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel + ".rewardchestlocation");
        section.set("x", point3D.getX());
        section.set("y", point3D.getY());
        section.set("z", point3D.getZ());
        section.set("world", point3D.getWorld());
        configProvider.save();
    }

    public boolean removeRewardChestLocation(int floorLevel) {
        Floor1BossSettings floor1BossSettings = (Floor1BossSettings) gameSettings.getBossSettings(floorLevel);
        if (floor1BossSettings.getRewardChestLocation() == null) {
            return false;
        }
        floor1BossSettings.setRewardChestLocation(null);
        ConfigSection section = configProvider.getSection("bosssettings.floor" + floorLevel);
        section.set("rewardchestlocation", null);
        return true;
    }
}
