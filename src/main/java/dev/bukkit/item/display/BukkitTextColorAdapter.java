package dev.bukkit.item.display;

import org.bukkit.ChatColor;

import dev.core.item.display.TextColor;

public class BukkitTextColorAdapter {

    public static String toChatColor(TextColor color) {
        return switch (color) {
        case BLACK -> ChatColor.BLACK.toString();
        case DARK_BLUE -> ChatColor.DARK_BLUE.toString();
        case DARK_GREEN -> ChatColor.DARK_GREEN.toString();
        case DARK_AQUA -> ChatColor.DARK_AQUA.toString();
        case DARK_RED -> ChatColor.DARK_RED.toString();
        case DARK_PURPLE -> ChatColor.DARK_PURPLE.toString();
        case GOLD -> ChatColor.GOLD.toString();
        case GRAY -> ChatColor.GRAY.toString();
        case DARK_GRAY -> ChatColor.DARK_GRAY.toString();
        case BLUE -> ChatColor.BLUE.toString();
        case GREEN -> ChatColor.GREEN.toString();
        case AQUA -> ChatColor.AQUA.toString();
        case RED -> ChatColor.RED.toString();
        case LIGHT_PURPLE -> ChatColor.LIGHT_PURPLE.toString();
        case YELLOW -> ChatColor.YELLOW.toString();
        case WHITE -> ChatColor.WHITE.toString();
        };
    }

    public static String colored(TextColor color, String text) {
        return toChatColor(color) + text;
    }

}
