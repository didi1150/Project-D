package dev.core.game.settings;

import dev.core.game.coords.Point3D;
import dev.core.game.coords.ViewPoint3D;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter @Setter
public class Floor1BossSettings extends BossSettings {
    private Map<Integer, Point3D> pillarLocations = new HashMap<>();
    private Map<Integer, Point3D> chainLinkLocations = new HashMap<>();
    private int heightThreshold50Percent;
    private int heightThreshold100Percent;
    private Set<Point3D> crateSpawnerLocations = new HashSet<>();
    private Set<Point3D> stalactiteLocations = new HashSet<>();
    private Set<Point3D> mobWaveSpawnLocations = new HashSet<>();
    private ViewPoint3D rewardPlayerSpawnLocation;
    private Point3D rewardChestLocation;

    public Floor1BossSettings(int floor) {
        super(floor);
    }

    public Point3D getPillarLocation(int number) {
        return pillarLocations.get(number);
    }

    public Point3D getChainLinkLocation(int number) {
        return chainLinkLocations.get(number);
    }
}
