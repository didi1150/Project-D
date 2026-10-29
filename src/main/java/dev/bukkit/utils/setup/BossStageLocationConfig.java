package dev.bukkit.utils.setup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.bukkit.configuration.ConfigurationSection;

import dev.bukkit.entity.boss.stage.BossGrowStageLocations;
import dev.bukkit.storage.BukkitConfigProvider;
import dev.core.entity.boss.BossDefinition;
import dev.core.entity.boss.BossDefinitionRegistry;
import dev.core.game.coords.Point3D;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/**
 * Reads and writes the arena layout stored on a {@code GROW} stage entry in
 * {@code bosses.yml}. The stage list is a YAML list of maps, so writes go
 * through the Bukkit configuration handle instead of the read-only
 * {@link ConfigSection} list wrappers.
 */
public final class BossStageLocationConfig {

    private final ConfigProvider provider;
    private final ConfigurationSection handle;

    public BossStageLocationConfig(ConfigProvider provider) {
        if (!(provider instanceof BukkitConfigProvider bukkitProvider)) {
            throw new IllegalArgumentException("Boss stage locations require a BukkitConfigProvider");
        }
        this.provider = provider;
        this.handle = bukkitProvider.getHandle();
    }

    BossStageLocationConfig(ConfigProvider provider, ConfigurationSection handle) {
        this.provider = provider;
        this.handle = handle;
    }

    public Optional<BossGrowStageLocations> load(int floor) {
        String bossId = bossIdForFloor(floor);
        if (bossId == null) {
            return Optional.empty();
        }
        ConfigSection bossesSection = provider.getRoot().getSection("bosses");
        if (bossesSection == null) {
            return Optional.empty();
        }
        ConfigSection floorSection = bossesSection.getSection(String.valueOf(floor));
        if (floorSection == null || !floorSection.getKeys().contains(bossId)) {
            return Optional.empty();
        }
        ConfigSection bossSection = floorSection.getSection(bossId);
        if (bossSection == null) {
            return Optional.empty();
        }
        for (ConfigSection stage : bossSection.getSectionList("stages")) {
            if ("GROW".equalsIgnoreCase(stage.getString("type", ""))) {
                return Optional.of(BossGrowStageLocations.from(stage));
            }
        }
        return Optional.empty();
    }

    public boolean setPillarLocation(int floor, int number, Point3D pos) {
        return updateGrowStage(floor, stage -> {
            mapAt(stage, BossGrowStageLocations.PILLAR_LOCATIONS).put(String.valueOf(number), toMap(pos));
            return true;
        });
    }

    public boolean removePillarLocation(int floor, Point3D pos) {
        return updateGrowStage(floor, stage -> mapAt(stage, BossGrowStageLocations.PILLAR_LOCATIONS)
                .entrySet().removeIf(entry -> pos.equals(fromMap(entry.getValue()))));
    }

    public boolean setChainLinkLocation(int floor, int number, Point3D pos) {
        return updateGrowStage(floor, stage -> {
            mapAt(stage, BossGrowStageLocations.CHAIN_LINK_LOCATIONS).put(String.valueOf(number), toMap(pos));
            return true;
        });
    }

    public boolean removeChainLinkLocation(int floor, Point3D pos) {
        return updateGrowStage(floor, stage -> mapAt(stage, BossGrowStageLocations.CHAIN_LINK_LOCATIONS)
                .entrySet().removeIf(entry -> pos.equals(fromMap(entry.getValue()))));
    }

    public boolean addCrateSpawnerLocation(int floor, Point3D pos) {
        return addPoint(floor, BossGrowStageLocations.CRATE_SPAWNER_LOCATIONS, pos);
    }

    public boolean removeCrateSpawnerLocation(int floor, Point3D pos) {
        return removePoint(floor, BossGrowStageLocations.CRATE_SPAWNER_LOCATIONS, pos);
    }

    public boolean addStalactiteLocation(int floor, Point3D pos) {
        return addPoint(floor, BossGrowStageLocations.STALACTITE_LOCATIONS, pos);
    }

    public boolean removeStalactiteLocation(int floor, Point3D pos) {
        return removePoint(floor, BossGrowStageLocations.STALACTITE_LOCATIONS, pos);
    }

    public boolean addMobWaveSpawnLocation(int floor, Point3D pos) {
        return addPoint(floor, BossGrowStageLocations.MOB_WAVE_SPAWN_LOCATIONS, pos);
    }

    public boolean removeMobWaveSpawnLocation(int floor, Point3D pos) {
        return removePoint(floor, BossGrowStageLocations.MOB_WAVE_SPAWN_LOCATIONS, pos);
    }

    public boolean setCrateSpawnerLocations(int floor, Set<Point3D> locations) {
        return setPointList(floor, BossGrowStageLocations.CRATE_SPAWNER_LOCATIONS, locations);
    }

    public boolean setStalactiteLocations(int floor, Set<Point3D> locations) {
        return setPointList(floor, BossGrowStageLocations.STALACTITE_LOCATIONS, locations);
    }

    public boolean setMobWaveSpawnLocations(int floor, Set<Point3D> locations) {
        return setPointList(floor, BossGrowStageLocations.MOB_WAVE_SPAWN_LOCATIONS, locations);
    }

    private boolean addPoint(int floor, String path, Point3D pos) {
        return updateGrowStage(floor, stage -> {
            List<Object> list = listAt(stage, path);
            if (containsPoint(list, pos)) {
                return false;
            }
            list.add(toMap(pos));
            return true;
        });
    }

    private boolean removePoint(int floor, String path, Point3D pos) {
        return updateGrowStage(floor, stage -> listAt(stage, path)
                .removeIf(entry -> pos.equals(fromMap(entry))));
    }

    private boolean setPointList(int floor, String path, Set<Point3D> locations) {
        return updateGrowStage(floor, stage -> {
            List<Object> list = new ArrayList<>();
            if (locations != null) {
                locations.forEach(point -> list.add(toMap(point)));
            }
            stage.put(path, list);
            return true;
        });
    }

    private boolean updateGrowStage(int floor, Predicate<Map<String, Object>> mutator) {
        String path = stageListPath(floor);
        if (path == null) {
            return false;
        }
        List<Map<String, Object>> stages = new ArrayList<>();
        for (Map<?, ?> raw : handle.getMapList(path)) {
            stages.add(mutableMap(raw));
        }
        Map<String, Object> growStage = stages.stream()
                .filter(stage -> "GROW".equalsIgnoreCase(String.valueOf(stage.get("type"))))
                .findFirst()
                .orElse(null);
        if (growStage == null || !mutator.test(growStage)) {
            return false;
        }
        handle.set(path, stages);
        provider.save();
        return true;
    }

    private String stageListPath(int floor) {
        String bossId = bossIdForFloor(floor);
        return bossId == null ? null : "bosses." + floor + "." + bossId + ".stages";
    }

    private String bossIdForFloor(int floor) {
        Optional<BossDefinition> definition = BossDefinitionRegistry.getInstance().getForFloor(floor);
        if (definition.isPresent()) {
            return definition.get().getId();
        }
        ConfigurationSection floorSection = handle.getConfigurationSection("bosses." + floor);
        if (floorSection == null) {
            return null;
        }
        return floorSection.getKeys(false).stream().findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapAt(Map<String, Object> parent, String path) {
        Object current = parent.get(path);
        if (current instanceof Map<?, ?>) {
            return (Map<String, Object>) current;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        parent.put(path, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listAt(Map<String, Object> parent, String path) {
        Object current = parent.get(path);
        if (current instanceof List<?>) {
            return (List<Object>) current;
        }
        List<Object> created = new ArrayList<>();
        parent.put(path, created);
        return created;
    }

    private static boolean containsPoint(List<Object> list, Point3D pos) {
        return list.stream().anyMatch(entry -> pos.equals(fromMap(entry)));
    }

    private static Map<String, Object> toMap(Point3D point) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", point.getX());
        map.put("y", point.getY());
        map.put("z", point.getZ());
        map.put("world", point.getWorld());
        return map;
    }

    private static Point3D fromMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        return new Point3D(intValue(map.get("x")), intValue(map.get("y")), intValue(map.get("z")),
                map.get("world") == null ? null : String.valueOf(map.get("world")));
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Map<String, Object> mutableMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), mutableValue(entry.getValue()));
        }
        return result;
    }

    private static Object mutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return mutableMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object entry : list) {
                result.add(mutableValue(entry));
            }
            return result;
        }
        return value;
    }
}
