package dev.bukkit.utils.setup;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import dev.bukkit.entity.boss.stage.BossGrowStageLocations;
import dev.core.game.coords.Point3D;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/**
 * Moves the legacy floor-1 arena layout from {@code setup.yml}
 * ({@code bosssettings.floor1.*}) into the floor's {@code GROW} stage entry in
 * {@code bosses.yml}. Existing stage values always win.
 */
public final class BossStageLocationMigration {

    private BossStageLocationMigration() {
    }

    public static int migrate(BossStageLocationConfig stageConfig, ConfigProvider setupProvider, int floor) {
        ConfigSection bossSettingsRoot = setupProvider.getRoot().getSection("bosssettings");
        if (bossSettingsRoot == null || !bossSettingsRoot.getKeys().contains("floor" + floor)) {
            return 0;
        }
        ConfigSection floorSection = bossSettingsRoot.getSection("floor" + floor);
        BossGrowStageLocations current = stageConfig.load(floor).orElse(BossGrowStageLocations.empty());
        int migrated = 0;

        Map<Integer, Point3D> pillars = readNumberedPoints(floorSection, "pillarlocations");
        if (current.pillarLocations().isEmpty() && !pillars.isEmpty()) {
            if (writePillars(stageConfig, floor, pillars)) {
                migrated += pillars.size();
                floorSection.set("pillarlocations", null);
            }
        } else {
            floorSection.set("pillarlocations", null);
        }

        Map<Integer, Point3D> chainLinks = readNumberedPoints(floorSection, "chainlinklocations");
        if (current.chainLinkLocations().isEmpty() && !chainLinks.isEmpty()) {
            if (writeChainLinks(stageConfig, floor, chainLinks)) {
                migrated += chainLinks.size();
                floorSection.set("chainlinklocations", null);
            }
        } else {
            floorSection.set("chainlinklocations", null);
        }

        Set<Point3D> crates = readPointList(floorSection, "cratespawnerlocations");
        if (current.crateSpawnerLocations().isEmpty() && !crates.isEmpty()) {
            if (writePoints(crates, point -> stageConfig.addCrateSpawnerLocation(floor, point))) {
                migrated += crates.size();
                floorSection.set("cratespawnerlocations", null);
            }
        } else {
            floorSection.set("cratespawnerlocations", null);
        }

        Set<Point3D> stalactites = readPointList(floorSection, "stalactitelocations");
        if (current.stalactiteLocations().isEmpty() && !stalactites.isEmpty()) {
            if (writePoints(stalactites, point -> stageConfig.addStalactiteLocation(floor, point))) {
                migrated += stalactites.size();
                floorSection.set("stalactitelocations", null);
            }
        } else {
            floorSection.set("stalactitelocations", null);
        }

        Set<Point3D> mobSpawns = readPointList(floorSection, "mobwavespawnlocations");
        if (current.mobWaveSpawnLocations().isEmpty() && !mobSpawns.isEmpty()) {
            if (writePoints(mobSpawns, point -> stageConfig.addMobWaveSpawnLocation(floor, point))) {
                migrated += mobSpawns.size();
                floorSection.set("mobwavespawnlocations", null);
            }
        } else {
            floorSection.set("mobwavespawnlocations", null);
        }

        setupProvider.save();
        return migrated;
    }

    private static boolean writePillars(BossStageLocationConfig stageConfig, int floor,
            Map<Integer, Point3D> locations) {
        for (Map.Entry<Integer, Point3D> entry : locations.entrySet()) {
            if (!stageConfig.setPillarLocation(floor, entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean writeChainLinks(BossStageLocationConfig stageConfig, int floor,
            Map<Integer, Point3D> locations) {
        for (Map.Entry<Integer, Point3D> entry : locations.entrySet()) {
            if (!stageConfig.setChainLinkLocation(floor, entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean writePoints(Set<Point3D> locations, PointWriter writer) {
        for (Point3D point : locations) {
            if (!writer.write(point)) {
                return false;
            }
        }
        return true;
    }

    private static Map<Integer, Point3D> readNumberedPoints(ConfigSection floorSection, String key) {
        Map<Integer, Point3D> out = new LinkedHashMap<>();
        ConfigSection section = floorSection.getSection(key);
        if (section == null) {
            return out;
        }
        for (String sectionKey : section.getKeys()) {
            ConfigSection point = section.getSection(sectionKey);
            if (point == null) {
                continue;
            }
            try {
                out.put(Integer.parseInt(sectionKey), readPoint(point));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static Set<Point3D> readPointList(ConfigSection floorSection, String key) {
        Set<Point3D> out = new LinkedHashSet<>();
        for (ConfigSection section : floorSection.getSectionList(key)) {
            out.add(readPoint(section));
        }
        return out;
    }

    private static Point3D readPoint(ConfigSection section) {
        return new Point3D(section.getInt("x", 0), section.getInt("y", 0), section.getInt("z", 0),
                section.getString("world", null));
    }

    @FunctionalInterface
    private interface PointWriter {
        boolean write(Point3D point);
    }
}
