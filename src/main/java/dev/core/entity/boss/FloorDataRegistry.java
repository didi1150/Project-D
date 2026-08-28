package dev.core.entity.boss;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for per-floor {@link FloorData} loaded from the top-level
 * {@code floor-data} section in {@code bosses.yml}.
 */
public class FloorDataRegistry {

    private static FloorDataRegistry instance;

    private final Map<Integer, FloorData> byFloor = new HashMap<>();

    public static synchronized FloorDataRegistry getInstance() {
        if (instance == null) {
            instance = new FloorDataRegistry();
        }
        return instance;
    }

    public synchronized void registerAll(Map<Integer, FloorData> map) {
        if (map == null) return;
        byFloor.putAll(map);
    }

    public synchronized void clear() {
        byFloor.clear();
    }

    public synchronized void replaceAll(Map<Integer, FloorData> map) {
        clear();
        registerAll(map);
    }

    public synchronized Optional<FloorData> get(int floor) {
        return Optional.ofNullable(byFloor.get(floor));
    }

    public synchronized FloorData getOrEmpty(int floor) {
        FloorData fd = byFloor.get(floor);
        return fd != null ? fd : FloorData.empty(floor);
    }

    public synchronized Map<Integer, FloorData> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(byFloor));
    }

    public synchronized int size() {
        return byFloor.size();
    }
}
