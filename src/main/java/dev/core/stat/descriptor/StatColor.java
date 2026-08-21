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
     * Parses a config string into a StatColor: {@code #RRGGBB} / {@code 0xRRGGBB}
     * hex values become custom colors, anything else is treated as a named
     * {@link TextColor}. Returns {@code null} when the value cannot be parsed.
     */
    public static StatColor fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
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