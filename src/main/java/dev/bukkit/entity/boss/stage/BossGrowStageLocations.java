package dev.bukkit.entity.boss.stage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import dev.core.game.coords.Point3D;
import dev.core.storage.config.ConfigSection;

/**
 * Arena layout configured on a {@code GROW} boss stage. The stage is the
 * single source of truth for these locations; floor-wide locations stay in
 * {@code setup.yml}.
 */
public final class BossGrowStageLocations {

    public static final String PILLAR_LOCATIONS = "pillar-locations";
    public static final String CHAIN_LINK_LOCATIONS = "chain-link-locations";
    public static final String CRATE_SPAWNER_LOCATIONS = "crate-spawner-locations";
    public static final String STALACTITE_LOCATIONS = "stalactite-locations";
    public static final String MOB_WAVE_SPAWN_LOCATIONS = "mob-wave-spawn-locations";

    private final Map<Integer, Point3D> pillarLocations;
    private final Map<Integer, Point3D> chainLinkLocations;
    private final Set<Point3D> crateSpawnerLocations;
    private final Set<Point3D> stalactiteLocations;
    private final Set<Point3D> mobWaveSpawnLocations;

    public BossGrowStageLocations(Map<Integer, Point3D> pillarLocations,
            Map<Integer, Point3D> chainLinkLocations, Set<Point3D> crateSpawnerLocations,
            Set<Point3D> stalactiteLocations, Set<Point3D> mobWaveSpawnLocations) {
        this.pillarLocations = copyMap(pillarLocations);
        this.chainLinkLocations = copyMap(chainLinkLocations);
        this.crateSpawnerLocations = copySet(crateSpawnerLocations);
        this.stalactiteLocations = copySet(stalactiteLocations);
        this.mobWaveSpawnLocations = copySet(mobWaveSpawnLocations);
    }

    public static BossGrowStageLocations empty() {
        return new BossGrowStageLocations(Map.of(), Map.of(), Set.of(), Set.of(), Set.of());
    }

    public static BossGrowStageLocations from(ConfigSection params) {
        if (params == null) {
            return empty();
        }
        return new BossGrowStageLocations(
                readNumberedPoints(params, PILLAR_LOCATIONS),
                readNumberedPoints(params, CHAIN_LINK_LOCATIONS),
                readPointList(params, CRATE_SPAWNER_LOCATIONS),
                readPointList(params, STALACTITE_LOCATIONS),
                readPointList(params, MOB_WAVE_SPAWN_LOCATIONS));
    }

    public Map<Integer, Point3D> pillarLocations() {
        return Collections.unmodifiableMap(pillarLocations);
    }

    public Map<Integer, Point3D> chainLinkLocations() {
        return Collections.unmodifiableMap(chainLinkLocations);
    }

    public Set<Point3D> crateSpawnerLocations() {
        return Collections.unmodifiableSet(crateSpawnerLocations);
    }

    public Set<Point3D> stalactiteLocations() {
        return Collections.unmodifiableSet(stalactiteLocations);
    }

    public Set<Point3D> mobWaveSpawnLocations() {
        return Collections.unmodifiableSet(mobWaveSpawnLocations);
    }

    private static Map<Integer, Point3D> readNumberedPoints(ConfigSection params, String path) {
        Map<Integer, Point3D> out = new LinkedHashMap<>();
        ConfigSection section = params.getSection(path);
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys()) {
            ConfigSection point = section.getSection(key);
            if (point == null) {
                continue;
            }
            try {
                out.put(Integer.parseInt(key), readPoint(point));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static Set<Point3D> readPointList(ConfigSection params, String path) {
        Set<Point3D> out = new LinkedHashSet<>();
        for (ConfigSection section : params.getSectionList(path)) {
            out.add(readPoint(section));
        }
        return out;
    }

    private static Point3D readPoint(ConfigSection section) {
        return new Point3D(section.getInt("x", 0), section.getInt("y", 0), section.getInt("z", 0),
                section.getString("world", null));
    }

    private static Map<Integer, Point3D> copyMap(Map<Integer, Point3D> source) {
        return source == null ? Map.of() : new LinkedHashMap<>(source);
    }

    private static Set<Point3D> copySet(Set<Point3D> source) {
        return source == null ? Set.of() : new LinkedHashSet<>(source);
    }
}
