package dev.core.reward;

import java.util.List;

/**
 * One entry in the reward case pool.
 *
 * <p>
 * {@link RewardType#CLASS_XP_DYE} rewards are visual dye icons that grant class
 * XP on claim. {@link RewardType#ITEM_UNLOCK} rewards unlock a reward-only RPG
 * item and (optionally) give one physical copy.
 */
public record RewardDefinition(String id, RewardType type, int weight, String displayName, List<String> description,
        int xpMin, int xpMax, String itemId, boolean giveItem) {

    public RewardDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Reward id must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Reward type must not be null for " + id);
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("Reward weight must be positive for " + id);
        }
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        description = description == null ? List.of() : List.copyOf(description);
        xpMin = Math.max(0, xpMin);
        xpMax = Math.max(xpMin, xpMax);
        if (type == RewardType.ITEM_UNLOCK && (itemId == null || itemId.isBlank())) {
            throw new IllegalArgumentException("Item unlock reward " + id + " needs an item-id");
        }
    }

    public boolean isXpDye() {
        return type == RewardType.CLASS_XP_DYE;
    }

    public boolean isItemUnlock() {
        return type == RewardType.ITEM_UNLOCK;
    }

    public static RewardDefinition xpDye(String id, int weight, String displayName, List<String> description, int xpMin,
            int xpMax) {
        return new RewardDefinition(id, RewardType.CLASS_XP_DYE, weight, displayName, description, xpMin, xpMax, null,
                false);
    }

    public static RewardDefinition itemUnlock(String id, int weight, String displayName, List<String> description,
            String itemId, boolean giveItem) {
        return new RewardDefinition(id, RewardType.ITEM_UNLOCK, weight, displayName, description, 0, 0, itemId,
                giveItem);
    }
}
