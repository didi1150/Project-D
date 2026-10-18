package dev.core.entity.boss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import dev.core.game.coords.ViewPoint3D;
import dev.core.storage.config.ConfigSection;

/**
 * Per-floor free-form metadata loaded from the top-level {@code floor-data}
 * section in {@code bosses.yml}. Each floor is keyed by floor number and may
 * contain arbitrary keys. Handled manually by floor-specific stage code.
 *
 * <pre>
 * floor-data:
 *   1:
 *     wipe-height: 100.0
 *     pillars:
 *       - { x: 10, y: 64, z: 10 }
 * </pre>
 */
public final class FloorData {

    private final int floor;
    private final ConfigSection section;

    public FloorData(int floor, ConfigSection section) {
        this.floor = floor;
        this.section = section;
    }

    public static FloorData empty(int floor) {
        return new FloorData(floor, null);
    }

    public int getFloor() {
        return floor;
    }

    public boolean isEmpty() {
        return section == null || section.getKeys().isEmpty();
    }

    public ConfigSection raw() {
        return section;
    }

    public String getString(String path, String def) {
        if (section == null)
            return def;
        return section.getString(path, def);
    }

    public int getInt(String path, int def) {
        if (section == null)
            return def;
        return section.getInt(path, def);
    }

    public double getDouble(String path, double def) {
        if (section == null)
            return def;
        return section.getDouble(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        if (section == null)
            return def;
        return section.getBoolean(path, def);
    }

    public List<String> getStringList(String path) {
        if (section == null)
            return Collections.emptyList();
        return section.getStringList(path);
    }

    public ConfigSection getSection(String path) {
        if (section == null)
            return null;
        return section.getSection(path);
    }

    public List<ConfigSection> getSectionList(String path) {
        if (section == null)
            return Collections.emptyList();
        return section.getSectionList(path);
    }

    public Set<String> getKeys() {
        if (section == null)
            return Collections.emptySet();
        return section.getKeys();
    }

    // ViewPoint / Point helpers

    public ViewPoint3D getViewPoint(String path) {
        ConfigSection s = getSection(path);
        if (s == null)
            return null;
        if (s.getKeys().isEmpty())
            return null;
        return toViewPoint(s);
    }

    public List<ViewPoint3D> getViewPointList(String path) {
        List<ConfigSection> secs = getSectionList(path);
        if (secs.isEmpty())
            return Collections.emptyList();
        List<ViewPoint3D> out = new ArrayList<>(secs.size());
        for (ConfigSection sec : secs) {
            out.add(toViewPoint(sec));
        }
        return out;
    }

    private static ViewPoint3D toViewPoint(ConfigSection s) {
        String world = s.getString("world", null);
        int x = s.getInt("x", 0);
        int y = s.getInt("y", 0);
        int z = s.getInt("z", 0);
        float yaw = (float) s.getDouble("yaw", 0);
        float pitch = (float) s.getDouble("pitch", 0);
        return new ViewPoint3D(x, y, z, world, yaw, pitch);
    }

}
