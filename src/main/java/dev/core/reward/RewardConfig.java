package dev.core.reward;

import java.util.List;
import java.util.Random;

public final class RewardConfig {

    private final double rollMinSeconds;
    private final double rollMaxSeconds;
    private final double rollTopSpeed;
    private final List<RewardDefinition> rewards;

    public RewardConfig(double rollMinSeconds, double rollMaxSeconds, double rollTopSpeed,
            List<RewardDefinition> rewards) {
        this.rollMinSeconds = Math.max(0.05, rollMinSeconds);
        this.rollMaxSeconds = Math.max(this.rollMinSeconds, rollMaxSeconds);
        this.rollTopSpeed = Math.max(0.1, rollTopSpeed);
        this.rewards = rewards == null ? List.of() : List.copyOf(rewards);
    }

    public double getRollMinSeconds() {
        return rollMinSeconds;
    }

    public double getRollMaxSeconds() {
        return rollMaxSeconds;
    }

    /**
     * Peak reel speed in slots per second. This is independent of the roll
     * duration: changing the duration changes the travel distance, not the top
     * speed.
     */
    public double getRollTopSpeed() {
        return rollTopSpeed;
    }

    public List<RewardDefinition> getRewards() {
        return rewards;
    }

    /**
     * A random roll duration in ticks, inclusive of both configured endpoints.
     */
    public int randomRollTicks(Random random) {
        int minTicks = Math.max(1, (int) Math.round(rollMinSeconds * 20.0));
        int maxTicks = Math.max(minTicks, (int) Math.round(rollMaxSeconds * 20.0));
        if (maxTicks == minTicks) {
            return minTicks;
        }
        return minTicks + random.nextInt(maxTicks - minTicks + 1);
    }
}
