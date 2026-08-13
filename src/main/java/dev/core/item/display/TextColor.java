package dev.core.item.display;

public enum TextColor {
    GOLD, GRAY, BLUE, GREEN, YELLOW, DARK_GRAY, DARK_PURPLE, LIGHT_PURPLE, BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA,
    DARK_RED, AQUA, RED, WHITE;

    public String toHex() {
        return switch (this) {
        case GOLD -> "#FFD700";
        case GRAY -> "#AAAAAA";
        case BLUE -> "#0000FF";
        case GREEN -> "#00FF00";
        case YELLOW -> "#FFFF00";
        case DARK_GRAY -> "#555555";
        case DARK_PURPLE -> "#800080";
        case LIGHT_PURPLE -> "#FF55FF";
        case BLACK -> "#000000";
        case DARK_BLUE -> "#0000AA";
        case DARK_GREEN -> "#00AA00";
        case DARK_AQUA -> "#00AAAA";
        case DARK_RED -> "#AA0000";
        case AQUA -> "#00FFFF";
        case RED -> "#FF0000";
        case WHITE -> "#FFFFFF";
        };
    }

    public static TextColor fromString(String s) {
        try {
            return TextColor.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
