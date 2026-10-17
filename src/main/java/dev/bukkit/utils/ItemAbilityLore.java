package dev.bukkit.utils;

import java.util.List;

import dev.core.utils.LegacyColors;

public record ItemAbilityLore(InterActionType interActionType, String ability, String description) {
    public List<String> getLore() {
        return List.of(LegacyColors.YELLOW + ability + " " + LegacyColors.DARK_GRAY + LegacyColors.BOLD + interActionType.toString(),
                LegacyColors.GRAY + description);
    }
}