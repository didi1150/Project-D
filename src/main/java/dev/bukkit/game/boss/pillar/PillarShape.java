package dev.bukkit.game.boss.pillar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PillarShape {

    public record Offset(int x, int y, int z) {
    }

    private PillarShape() {
    }

    public static List<Offset> offsets(int height) {
        int totalHeight = Math.max(1, height);
        Set<Offset> offsets = new LinkedHashSet<>();

        for (int y = 0; y < totalHeight; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    offsets.add(new Offset(x, y, z));
                }
            }
        }

        return List.copyOf(new ArrayList<>(offsets));
    }
}
