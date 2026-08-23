package dev.core.stat.descriptor;

import dev.core.item.display.TextColor;

/**
 * A stat color: either a named {@link TextColor} or a custom RGB hex color
 * ({@code #RRGGBB}). Custom hex colors are supported for display purposes
 * (chat codes, lore) while named colors also map to the legacy TextColor API.
 */
public final class StatColor {

    private final TextColor named;
    private final String hex;

    private StatColor(TextColor named, String hex) {
        this.named = named;
        this.hex = hex;
    }

    /**
     * Named color factory.
     */
    public static StatColor named(TextColor named) {
        return new StatColor(named, null);
    }

    /**
     * Custom hex color factory. The input must be a valid {@code #RRGGBB} code.
     */
    public static StatColor of(String hex) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("Invalid hex color: " + hex);
        }
        return new StatColor(null, hex.toUpperCase());
    }

    /**
     * Parses a config string into a StatColor. Accepted notations:
     * <ul>
     * <li>hex: {@code #RRGGBB}, {@code 0xRRGGBB}, {@code &#RRGGBB}</li>
     * <li>RGB function: {@code rgb(r,g,b)}</li>
     * <li>named colors: {@code gold}, {@code dark_purple}, … (case-insensitive
     * {@link TextColor} names)</li>
     * <li>legacy codes: {@code &a}, {@code &4}, …</li>
     * </ul>
     * Returns {@code null} when the value cannot be parsed.
     */
    public static StatColor fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        // "&#RRGGBB" -> "#RRGGBB"
        if (value.startsWith("&#") && value.length() == 8) {
            value = value.substring(1);
        }
        String rgb = rgbHex(value);
        if (rgb != null) {
            return of(rgb);
        }
        TextColor legacy = legacyCode(value);
        if (legacy != null) {
            return named(legacy);
        }
        if (value.startsWith("#") || value.startsWith("0x")) {
            String digits = value.startsWith("0x") ? value.substring(2) : value.substring(1);
            if (digits.matches("[0-9A-Fa-f]{6}")) {
                return of("#" + digits);
            }
            return null;
        }
        TextColor named = TextColor.fromString(value);
        return named != null ? named(named) : null;
    }

    /**
     * Matches an {@code rgb(r,g,b)} token and returns normalized
     * {@code #RRGGBB}, or {@code null}.
     */
    private static String rgbHex(String value) {
        java.util.regex.Matcher m = RGB_PATTERN.matcher(value);
        if (!m.matches()) {
            return null;
        }
        try {
            int r = clampComponent(Integer.parseInt(m.group(1)));
            int g = clampComponent(Integer.parseInt(m.group(2)));
            int b = clampComponent(Integer.parseInt(m.group(3)));
            return String.format("#%02X%02X%02X", r, g, b);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final java.util.regex.Pattern RGB_PATTERN = java.util.regex.Pattern.compile(
            "&?rgb\\(\\s*(-?\\d{1,3})\\s*,\\s*(-?\\d{1,3})\\s*,\\s*(-?\\d{1,3})\\s*\\)",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Maps a single legacy code ({@code "&c"}, {@code "§e"}, …) to its named
     * {@link TextColor}, or {@code null}.
     */
    private static TextColor legacyCode(String value) {
        if (value.length() != 2) {
            return null;
        }
        char prefix = value.charAt(0);
        if (prefix != '&' && prefix != '\u00A7') {
            return null;
        }
        return switch (Character.toLowerCase(value.charAt(1))) {
            case '0' -> TextColor.BLACK;
            case '1' -> TextColor.DARK_BLUE;
            case '2' -> TextColor.DARK_GREEN;
            case '3' -> TextColor.DARK_AQUA;
            case '4' -> TextColor.DARK_RED;
            case '5' -> TextColor.DARK_PURPLE;
            case '6' -> TextColor.GOLD;
            case '7' -> TextColor.GRAY;
            case '8' -> TextColor.DARK_GRAY;
            case '9' -> TextColor.BLUE;
            case 'a' -> TextColor.GREEN;
            case 'b' -> TextColor.AQUA;
            case 'c' -> TextColor.RED;
            case 'd' -> TextColor.LIGHT_PURPLE;
            case 'e' -> TextColor.YELLOW;
            case 'f' -> TextColor.WHITE;
            default -> null;
        };
    }

    private static int clampComponent(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * True when this is a custom hex color (no named TextColor equivalent).
     */
    public boolean isCustom() {
        return hex != null;
    }

    /**
     * The named TextColor, or {@code null} for custom hex colors.
     */
    public TextColor getNamed() {
        return named;
    }

    /**
     * The {@code #RRGGBB} hex code (uppercase), or {@code null} for named colors.
     */
    public String getHex() {
        return hex;
    }

    /**
     * The hex representation of this color in either form.
     */
    public String toHex() {
        return hex != null ? hex : named.toHex();
    }
}