package dev.core.entity.boss;

import java.util.HashMap;
import java.util.Map;

import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/**
 * Loads the top-level {@code floor-data} map from {@code bosses.yml}. Shape:
 * {@code floor-data: <floorInt>: { arbitrary keys } }.
 *
 * <p>
 * Unknown keys are preserved and ignored here; floor-specific stage code
 * interprets them manually via {@link FloorData} helpers.
 * </p>
 */
public final class FloorDataLoader {

    private FloorDataLoader() {
    }

    public static Map<Integer, FloorData> loadAll(ConfigProvider provider) {
        Map<Integer, FloorData> out = new HashMap<>();
        ConfigSection root = provider.getRoot();
        if (root == null)
            return out;
        // Avoid auto-create side effect in BukkitConfigSection: only read if key exists
        if (!root.getKeys().contains("floor-data")) {
            return out;
        }
        ConfigSection fdRoot = root.getSection("floor-data");
        if (fdRoot == null || fdRoot.getKeys().isEmpty()) {
            return out;
        }
        for (String floorKey : fdRoot.getKeys()) {
            int floor;
            try {
                floor = Integer.parseInt(floorKey);
            } catch (NumberFormatException ignored) {
                continue;
            }
            ConfigSection floorSection = fdRoot.getSection(floorKey);
            // floorSection may be auto-created empty; keep it - FloorData wraps null/empty
            // safely
            out.put(floor, new FloorData(floor, floorSection));
        }
        return out;
    }
}
