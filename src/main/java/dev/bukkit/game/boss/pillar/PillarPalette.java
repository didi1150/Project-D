package dev.bukkit.game.boss.pillar;

import org.bukkit.Material;

public final class PillarPalette {

    private static final Material[] PRISTINE = {
            Material.QUARTZ_BLOCK,
            Material.QUARTZ_BRICKS,
            Material.CHISELED_QUARTZ_BLOCK,
            Material.SMOOTH_QUARTZ,
            Material.QUARTZ_PILLAR
    };

    private static final Material[] WORN = {
            Material.QUARTZ_BLOCK,
            Material.CALCITE,
            Material.DIORITE,
            Material.POLISHED_DIORITE,
            Material.CRACKED_STONE_BRICKS
    };

    private static final Material[] DAMAGED = {
            Material.POLISHED_DIORITE,
            Material.ANDESITE,
            Material.STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS,
            Material.COBBLESTONE
    };

    private static final Material[] CRUMBLING = {
            Material.ANDESITE,
            Material.COBBLESTONE,
            Material.GRAVEL,
            Material.COBBLED_DEEPSLATE,
            Material.TUFF
    };

    private PillarPalette() {
    }

    public static int bucket(double healthFraction) {
        double fraction = PillarMath.clamp01(healthFraction);
        if (fraction <= 0.0) {
            return 0;
        }
        if (fraction <= 0.25) {
            return 1;
        }
        if (fraction <= 0.50) {
            return 2;
        }
        if (fraction <= 0.75) {
            return 3;
        }
        return 4;
    }

    public static Material materialFor(double healthFraction, int x, int y, int z, int salt) {
        int bucket = bucket(healthFraction);
        if (bucket == 0) {
            return Material.AIR;
        }
        int hash = hash(x, y, z, salt);
        if (bucket <= 2 && Math.floorMod(hash, 100) < (bucket == 1 ? 12 : 6)) {
            return Material.AIR;
        }
        return switch (bucket) {
        case 4 -> PRISTINE[Math.floorMod(hash, PRISTINE.length)];
        case 3 -> WORN[Math.floorMod(hash, WORN.length)];
        case 2 -> DAMAGED[Math.floorMod(hash, DAMAGED.length)];
        default -> CRUMBLING[Math.floorMod(hash, CRUMBLING.length)];
        };
    }

    private static int hash(int x, int y, int z, int salt) {
        int hash = 17;
        hash = hash * 31 + x;
        hash = hash * 31 + y;
        hash = hash * 31 + z;
        hash = hash * 31 + salt;
        return hash;
    }
}
