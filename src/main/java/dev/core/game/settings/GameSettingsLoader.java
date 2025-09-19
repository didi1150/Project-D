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
    public static final String MINPLAYERS = "minplayers";
    private GameSettings gameSettings;
    private ConfigProvider configProvider;

    public GameSettingsLoader(GameSettings gameSettings, ConfigProvider configProvider) {
        this.gameSettings = gameSettings;
        this.configProvider = configProvider;
    }

    public void load() {
        boolean setupMode = configProvider.getRoot().getBoolean("setup", true);
        String dungeonWorld = configProvider.getRoot().getString("dungeonworld", "dungeonworld");
        ViewPoint3D preLobbySpawn = loadViewCoords(configProvider.getSection(LOCATIONS_PRELOBBYSPAWN));
        ViewPoint3D selectionSpawn = loadViewCoords(configProvider.getSection(LOCATIONS_SELECTIONSPAWN));
        Map<RPGClassType, Point3D> selectionClasses = new HashMap<>();
        ConfigSection selectionClassesSection = configProvider.getSection(LOCATIONS_SELECTIONCLASSES);
        for (String key : selectionClassesSection.getKeys()) {
            RPGClassType classType = RPGClassType.valueOf(key);
            Point3D coord = loadBlockCoords(selectionClassesSection.getSection(key));
            selectionClasses.put(classType, coord);
        }

        Point3D holeCenter = loadBlockCoords(configProvider.getSection(LOCATIONS_HOLECENTER));
        int minPlayers = configProvider.getRoot().getInt(MINPLAYERS, 1);

        gameSettings.setMinPlayers(minPlayers);
        gameSettings.setDungeonWorld(dungeonWorld);
        gameSettings.setSelectionSpawn(selectionSpawn);
        gameSettings.setPreLobbySpawn(preLobbySpawn);
        gameSettings.setHoleCenter(holeCenter);
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
        configProvider.getRoot().set("dungeonworld", world);
    }

    public void setMinPlayers(int count) {
        configProvider.getRoot().set(MINPLAYERS, count);
        configProvider.save();
    }
}
