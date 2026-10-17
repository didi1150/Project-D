package dev.bukkit.hud;

import dev.core.utils.AdventureText;

/**
 * Conservative server-side estimate of how many rendered lines a HUD string
 * occupies. The client owns the real font metrics, so this uses the default
 * Minecraft glyph widths for ASCII and a small safety margin. Overestimating is
 * intentional: an extra reserved line only adds spacing, while underestimating
 * would let HUD fragments overlap.
 */
final class HudTextLayout {

    private static final int LINE_WIDTH_MARGIN_PX = 4;
    private static final int NON_ASCII_GLYPH_WIDTH = 9;

    private HudTextLayout() {
    }

    /**
     * @return number of rendered lines, at least {@code 1}
     */
    static int lineCount(String legacyText, int lineWidth) {
        if (legacyText == null || legacyText.isEmpty()) {
            return 1;
        }
        String plain = AdventureText.plainOrEmpty(AdventureText.legacy(legacyText));
        if (plain.isEmpty()) {
            return 1;
        }

        int usableWidth = Math.max(1, lineWidth - LINE_WIDTH_MARGIN_PX);
        int total = 0;
        for (String line : plain.split("\\R", -1)) {
            int width = pixelWidth(line);
            total += width <= 0 ? 1 : (width + usableWidth - 1) / usableWidth;
        }
        return Math.max(1, total);
    }

    private static int pixelWidth(String line) {
        int width = 0;
        for (int i = 0; i < line.length();) {
            int codePoint = line.codePointAt(i);
            i += Character.charCount(codePoint);
            width += glyphWidth(codePoint);
        }
        return width;
    }

    /**
     * Approximate default-font widths in pixels. These mirror the vanilla
     * bitmap font closely enough to decide wrapping; non-ASCII glyphs get a
     * slightly wider conservative value.
     */
    private static int glyphWidth(int codePoint) {
        if (codePoint == ' ') {
            return 4;
        }
        if (codePoint == '\t') {
            return 16;
        }
        if (codePoint < 0x20 || codePoint == 0x7F) {
            return 0;
        }
        if (codePoint > 0x7E) {
            return NON_ASCII_GLYPH_WIDTH;
        }
        return switch (codePoint) {
            case '!', ',', '.', ':', ';', 'i' -> 2;
            case '\'', 'l' -> 3;
            case '[', ']', 't' -> 4;
            case '"', '(', ')', '*', '<', '>', 'f', 'k', '{', '}' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
