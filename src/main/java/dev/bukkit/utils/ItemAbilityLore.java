package dev.bukkit.utils;

import org.bukkit.ChatColor;

import java.util.List;

public record ItemAbilityLore(InterActionType interActionType, String ability, String description) {
    public List<String> getLore() {
        return List.of(ChatColor.YELLOW + ability + " " + ChatColor.DARK_GRAY + ChatColor.BOLD + interActionType.toString(),
                ChatColor.GRAY + description);
    }
}