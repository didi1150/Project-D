package dev.core.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class RewardPoolTest {

    @Test
    void rollExcludingNeverReturnsTheExcludedReward() {
        List<RewardDefinition> pool = List.of(
                RewardDefinition.xpDye("A", 1, "A", List.of(), 10, 20),
                RewardDefinition.xpDye("B", 1, "B", List.of(), 10, 20),
                RewardDefinition.xpDye("C", 1, "C", List.of(), 10, 20));
        Random random = new Random(42);

        for (int i = 0; i < 200; i++) {
            RewardDefinition reward = RewardPool.rollExcluding(pool, "B", random).orElseThrow();
            assertTrue(!"B".equals(reward.id()));
        }
    }

    @Test
    void nonPositiveWeightIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RewardDefinition.xpDye("ZERO", 0, "Zero", List.of(), 1, 2));
    }

    @Test
    void rollOnlyPicksFromProvidedPositiveWeights() {
        List<RewardDefinition> pool = List.of(
                RewardDefinition.xpDye("A", 1, "A", List.of(), 1, 2),
                RewardDefinition.xpDye("B", 5, "B", List.of(), 1, 2));
        Random random = new Random(7);

        for (int i = 0; i < 100; i++) {
            RewardDefinition reward = RewardPool.roll(pool, random).orElseThrow();
            assertTrue(reward.id().equals("A") || reward.id().equals("B"));
        }
    }

    @Test
    void emptyPoolReturnsEmpty() {
        assertTrue(RewardPool.roll(List.of(), new Random(1)).isEmpty());
        assertTrue(RewardPool.rollExcluding(List.of(), "X", new Random(1)).isEmpty());
    }
}
