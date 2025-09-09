package dev.bukkit.item.display;

import dev.core.item.display.TextStyle;
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

    /**
     * Converts a TextStyle (color + formatters) into Bukkit ChatColor codes.
     */
    public static String toChatFormatting(TextStyle style) {
        StringBuilder sb = new StringBuilder();

        // Apply color
        sb.append(BukkitTextColorAdapter.toChatColor(style.color()));

        // Apply formatters
        for (TextStyle.TextFormatter fmt : style.formatters()) {
            switch (fmt) {
                case BOLD -> sb.append(ChatColor.BOLD);
                case ITALIC -> sb.append(ChatColor.ITALIC);
                case UNDERLINE -> sb.append(ChatColor.UNDERLINE);
                case STRIKETHROUGH -> sb.append(ChatColor.STRIKETHROUGH);
                case RESET -> sb.append(ChatColor.RESET).append(BukkitTextColorAdapter.toChatColor(style.color())); // restore
                // color
                // after
                // reset
            }
        }

        return sb.toString();
    }

}
