package dev.core.utils;

import dev.core.item.display.TextColor;

public enum MessageLevel {
    NORMAL_LEVEL(0, TextColor.WHITE),
    INFO_LEVEL(1, TextColor.YELLOW),
    WARNING_LEVEL(2, TextColor.GOLD),
    ERROR_LEVEL(3, TextColor.RED);

    private final int level;
    private final TextColor color;

    MessageLevel(int level, TextColor color) {
        this.level = level;
        this.color = color;
    }

    public int getLevel() {
        return level;
    }

    public TextColor getColor() {
        return color;
    }

    public String getPrefix() {
        String s = this.toString();
        return s.substring(0, s.indexOf('_'));
    }
}
