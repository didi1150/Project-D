package dev.core.game.settings;

import dev.core.game.coords.ViewPoint3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Floor1BossSettings extends BossSettings {
    private Map<Integer, ViewPoint3D> pillarLocations = new HashMap<>();
    private Map<Integer, ViewPoint3D> chainLinkLocations = new HashMap<>();
    private int heightThreshold50Percent;
    private int heightThreshold100Percent;
    private List<ViewPoint3D> crateSpawnerLocations = new ArrayList<>();
    private List<ViewPoint3D> stalactiteLocations = new ArrayList<>();
    private List<ViewPoint3D> mobWaveSpawnLocations = new ArrayList<>();
    private ViewPoint3D rewardPlayerSpawnLocation;
    private ViewPoint3D rewardChestLocation;

    public Floor1BossSettings(int floor) {
        super(floor);
    }

    public ViewPoint3D getPillarLocation(int number) {
        return pillarLocations.get(number);
    }

    public ViewPoint3D getChainLinkLocation(int number) {
        return chainLinkLocations.get(number);
    }
}
