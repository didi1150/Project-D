package dev.bukkit.utils;

import dev.core.item.display.TextColor;
import net.kyori.adventure.text.format.NamedTextColor;

public class AdventureColorConverter {

    public static NamedTextColor toAdventure(TextColor color) {
        if (color == null)
            return null;

        return switch (color) {
        case GOLD -> NamedTextColor.GOLD;
        case GRAY -> NamedTextColor.GRAY;
        case BLUE -> NamedTextColor.BLUE;
        case GREEN -> NamedTextColor.GREEN;
        case YELLOW -> NamedTextColor.YELLOW;
        case DARK_GRAY -> NamedTextColor.DARK_GRAY;
        case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
        case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
        case BLACK -> NamedTextColor.BLACK;
        case DARK_BLUE -> NamedTextColor.DARK_BLUE;
        case DARK_GREEN -> NamedTextColor.DARK_GREEN;
        case DARK_AQUA -> NamedTextColor.DARK_AQUA;
        case DARK_RED -> NamedTextColor.DARK_RED;
        case AQUA -> NamedTextColor.AQUA;
        case RED -> NamedTextColor.RED;
        case WHITE -> NamedTextColor.WHITE;
        };
    }
}