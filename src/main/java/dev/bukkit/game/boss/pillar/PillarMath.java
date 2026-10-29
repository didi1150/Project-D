package dev.bukkit.game.boss.pillar;

public final class PillarMath {

    private PillarMath() {
    }

    public static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static double aggregateFraction(double currentHp, double maxHp) {
        if (maxHp <= 0) {
            return 0.0;
        }
        return clamp01(currentHp / maxHp);
    }

    public static double weakestFraction(double[] fractions) {
        if (fractions == null || fractions.length == 0) {
            return 0.0;
        }
        double weakest = 1.0;
        for (double fraction : fractions) {
            weakest = Math.min(weakest, clamp01(fraction));
        }
        return weakest;
    }

    public static boolean isHolding(double weakestFraction, double holdThreshold) {
        return weakestFraction >= holdThreshold;
    }

    public static double heightIncrease(double aggregateFraction, double weakestFraction, double holdThreshold,
            double baseRate, double maxRate, double deltaSeconds, double fullHeight) {
        if (fullHeight <= 0) {
            return 1.0;
        }
        if (deltaSeconds <= 0 || isHolding(weakestFraction, holdThreshold)) {
            return 0.0;
        }
        double threshold = Math.max(0.01, Math.min(1.0, holdThreshold));
        double integrity = clamp01(aggregateFraction);
        double damage = clamp01((threshold - integrity) / threshold);
        double speed = Math.max(0.0, baseRate) + (Math.max(baseRate, maxRate) - Math.max(0.0, baseRate)) * damage;
        return speed * deltaSeconds / fullHeight;
    }

    public static double chainSag(double healthFraction, double tautSag, double slackSag) {
        double t = 1.0 - clamp01(healthFraction);
        return tautSag + (slackSag - tautSag) * t;
    }
}
