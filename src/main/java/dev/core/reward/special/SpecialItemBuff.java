package dev.core.reward.special;

import java.util.List;

/** A named collection of temporary stat modifiers granted as one random buff. */
public record SpecialItemBuff(String id, String displayName, double durationSeconds,
        List<SpecialItemBuffStat> stats) {

    public SpecialItemBuff {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Buff id must not be blank");
        }
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        durationSeconds = Math.max(0.05, durationSeconds);
        stats = stats == null ? List.of() : List.copyOf(stats);
    }

    public boolean isUsable() {
        return !stats.isEmpty();
    }
}
