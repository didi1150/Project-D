package dev.bukkit.item.display;

import dev.core.item.display.TextStyle;
import org.bukkit.ChatColor;

import dev.core.item.display.TextColor;
import dev.core.stat.StatType;
import dev.core.stat.adapter.StatTypeAdapter;
import dev.core.stat.descriptor.StatColor;

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

    /**
     * Converts an {@code #RRGGBB} hex color to its Bukkit chat color code
     * ({@code §x§R§R§G§G§B§B}).
     */
    public static String toHexCode(String hex) {
        String digits = hex.replace("#", "");
        StringBuilder sb = new StringBuilder("§x");
        for (int i = 0; i < digits.length(); i++) {
            sb.append('§').append(digits.charAt(i));
        }
        return sb.toString();
    }

    /**
     * Converts a {@link StatColor} to its Bukkit chat color code (hex codes for
     * custom colors, legacy codes otherwise).
     */
    public static String toChatCode(StatColor color) {
        if (color.isCustom()) {
            return toHexCode(color.getHex());
        }
        return toChatColor(color.getNamed());
    }

    private static void appendColor(StringBuilder sb, TextStyle style) {
        if (style.hexColor() != null) {
            sb.append(toHexCode(style.hexColor()));
        } else {
            sb.append(toChatColor(style.color()));
        }
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
        appendColor(sb, style);

        // Apply formatters
        for (TextStyle.TextFormatter fmt : style.formatters()) {
            switch (fmt) {
                case BOLD -> sb.append(ChatColor.BOLD);
                case ITALIC -> sb.append(ChatColor.ITALIC);
                case UNDERLINE -> sb.append(ChatColor.UNDERLINE);
                case STRIKETHROUGH -> sb.append(ChatColor.STRIKETHROUGH);
                case RESET -> sb.append(ChatColor.RESET).append(style.hexColor() != null
                        ? toHexCode(style.hexColor()) : toChatColor(style.color())); // restore color after reset
            }
        }

        return sb.toString();
    }

    /**
     * Formats a stat value with its registered descriptor metadata (symbol,
     * display name, color). Falls back to StatType defaults when no descriptor
     * is registered. The result is already colored.
     */
    public static String formatStat(StatType type, double value, boolean showPlus) {
        var descriptor = StatTypeAdapter.getDescriptor(type);
        if (descriptor.isPresent()) {
            return toChatCode(descriptor.get().getColor()) + descriptor.get().formatValue(value, showPlus);
        }
        return toChatColor(type.getColor()) + type.formatValue(value, showPlus);
    }

}