package dev.core.reward;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class RewardPool {

    private RewardPool() {
    }

    public static Optional<RewardDefinition> roll(List<RewardDefinition> rewards, Random random) {
        if (rewards == null || rewards.isEmpty()) {
            return Optional.empty();
        }
        int totalWeight = 0;
        for (RewardDefinition reward : rewards) {
            if (reward.weight() > 0) {
                totalWeight += reward.weight();
            }
        }
        if (totalWeight <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (RewardDefinition reward : rewards) {
            if (reward.weight() <= 0) {
                continue;
            }
            cumulative += reward.weight();
            if (roll < cumulative) {
                return Optional.of(reward);
            }
        }
        return Optional.of(rewards.get(rewards.size() - 1));
    }

    public static Optional<RewardDefinition> rollExcluding(List<RewardDefinition> rewards, String excludedId,
            Random random) {
        if (rewards == null || rewards.isEmpty()) {
            return Optional.empty();
        }
        List<RewardDefinition> filtered = rewards.stream()
                .filter(reward -> !reward.id().equals(excludedId))
                .toList();
        return roll(filtered, random);
    }
}
