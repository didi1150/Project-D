package dev.core.reward;

import java.util.ArrayList;
import java.util.List;

import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

public final class RewardConfigLoader {

    public static final double DEFAULT_ROLL_MIN_SECONDS = 4.0;
    public static final double DEFAULT_ROLL_MAX_SECONDS = 6.5;
    public static final double DEFAULT_ROLL_TOP_SPEED = 12.0;

    private RewardConfigLoader() {
    }

    public static RewardConfig load(ConfigProvider provider) {
        ConfigSection settings = provider.getRoot().getSection("settings");
        double minSeconds = settings == null ? DEFAULT_ROLL_MIN_SECONDS
                : settings.getDouble("roll-min-seconds", DEFAULT_ROLL_MIN_SECONDS);
        double maxSeconds = settings == null ? DEFAULT_ROLL_MAX_SECONDS
                : settings.getDouble("roll-max-seconds", DEFAULT_ROLL_MAX_SECONDS);
        double topSpeed = settings == null ? DEFAULT_ROLL_TOP_SPEED
                : settings.getDouble("roll-top-speed", DEFAULT_ROLL_TOP_SPEED);
        ConfigSection rewardsSection = provider.getRoot().getSection("rewards");
        List<RewardDefinition> rewards = new ArrayList<>();
        if (rewardsSection != null) {
            for (String id : rewardsSection.getKeys()) {
                ConfigSection section = rewardsSection.getSection(id);
                if (section == null) {
                    continue;
                }
                try {
                    rewards.add(parse(id, section));
                } catch (RuntimeException e) {
                    System.out.println("[Rewards] Skipping invalid reward '" + id + "': " + e.getMessage());
                }
            }
        }
        if (rewards.isEmpty()) {
            System.out.println("[Rewards] No valid rewards configured in rewards.yml.");
        }
        return new RewardConfig(minSeconds, maxSeconds, topSpeed, rewards);
    }

    private static RewardDefinition parse(String id, ConfigSection section) {
        String typeRaw = section.getString("type", "CLASS_XP_DYE");
        RewardType type;
        try {
            type = RewardType.valueOf(typeRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown type '" + typeRaw + "'");
        }

        int weight = section.getInt("weight", 1);
        String displayName = section.getString("display-name", id);
        List<String> description = section.getStringList("description");

        if (type == RewardType.CLASS_XP_DYE) {
            int xpMin = section.getInt("xp-min", 50);
            int xpMax = section.getInt("xp-max", xpMin);
            return RewardDefinition.xpDye(id, weight, displayName, description, xpMin, xpMax);
        }

        String itemId = section.getString("item-id", null);
        boolean giveItem = section.getBoolean("give-item", true);
        return RewardDefinition.itemUnlock(id, weight, displayName, description, itemId, giveItem);
    }
}
