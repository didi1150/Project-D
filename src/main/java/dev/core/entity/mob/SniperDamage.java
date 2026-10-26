package dev.core.entity.mob;

public final class SniperDamage {

    private SniperDamage() {
    }

    public static double distanceMultiplier(double distance, double growthPerBlock, double maxMultiplier) {
        double growth = Math.max(0.0, growthPerBlock);
        double distanceBlocks = Math.max(0.0, distance);
        double cap = Math.max(1.0, maxMultiplier);
        return Math.min(cap, Math.exp(growth * distanceBlocks));
    }

    public static double damage(double baseDamage, double distance, double growthPerBlock, double maxMultiplier) {
        return Math.max(0.0, baseDamage) * distanceMultiplier(distance, growthPerBlock, maxMultiplier);
    }
}
