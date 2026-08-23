package dev.core.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central color translation for config strings. Supports every standard
 * notation side by side:
 * <ul>
 * <li>legacy codes: {@code &0}-{@code &9}, {@code &a}-{@code &f},
 * {@code &k}-{@code &r} (and their {@code §} forms)</li>
 * <li>hex: {@code &#RRGGBB}, {@code {#RRGGBB}} and bare {@code #RRGGBB}
 * handled by value parsers ({@code 0xRRGGBB} too)</li>
 * <li>RGB functions: {@code rgb(r,g,b)} or {@code &rgb(r,g,b)}</li>
 * </ul>
 * Hex and RGB forms render through the vanilla {@code §x§R§R…} legacy escape,
 * so results work everywhere legacy strings are accepted (chat, item names,
 * TextDisplay lines).
 */
public final class ColorCodes {

    /** Vanilla section sign used by all legacy codes. */
    public static final char SECTION = '\u00A7';

    private static final String LEGACY_CHARS = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    private static final Pattern AMP_HEX = Pattern.compile("&#([0-9A-Fa-f]{6})");
    private static final Pattern BRACE_HEX = Pattern.compile("\\{#([0-9A-Fa-f]{6})}");
    private static final Pattern RGB_FUNC = Pattern.compile(
            "&?rgb\\(\\s*(-?\\d{1,3})\\s*,\\s*(-?\\d{1,3})\\s*,\\s*(-?\\d{1,3})\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private ColorCodes() {}

    /**
     * Full translation: {@code rgb(r,g,b)} functions, {@code &#RRGGBB} /
     * {@code {#RRGGBB}} hex escapes, then standard {@code &} codes.
     */
    public static String translate(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String out = replaceAll(input, RGB_FUNC, m -> toLegacy(rgbDigits(m.group(1), m.group(2), m.group(3))));
        out = replaceAll(out, AMP_HEX, m -> toLegacy(m.group(1)));
        out = replaceAll(out, BRACE_HEX, m -> toLegacy(m.group(1)));
        return translateAmp(out);
    }

    /**
     * Translates standard {@code &} codes only. Drop-in superset replacement
     * for {@code ChatColor.translateAlternateColorCodes('&', …)}.
     */
    public static String translateAmp(String input) {
        if (input == null || input.indexOf('&') < 0) {
            return input;
        }
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && LEGACY_CHARS.indexOf(chars[i + 1]) > -1) {
                chars[i] = SECTION;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    /**
     * Parses an {@code rgb(r,g,b)} token body into {@code RRGGBB} digits, or
     * {@code null}. Components clamp to 0-255.
     */
    public static String rgbDigits(String rRaw, String gRaw, String bRaw) {
        try {
            int r = clampComponent(Integer.parseInt(rRaw));
            int g = clampComponent(Integer.parseInt(gRaw));
            int b = clampComponent(Integer.parseInt(bRaw));
            return String.format("%02X%02X%02X", r, g, b);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Renders {@code RRGGBB} digits as the vanilla legacy hex escape
     * {@code §x§R§R§G§G§B§B}.
     */
    public static String toLegacy(String digits) {
        if (digits == null || !digits.matches("[0-9A-Fa-f]{6}")) {
            return "";
        }
        StringBuilder sb = new StringBuilder(14);
        sb.append(SECTION).append('x');
        for (int i = 0; i < digits.length(); i++) {
            sb.append(SECTION).append(Character.toLowerCase(digits.charAt(i)));
        }
        return sb.toString();
    }

    private static int clampComponent(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private interface ReplaceFn {
        String apply(Matcher m);
    }

    private static String replaceAll(String input, Pattern pattern, ReplaceFn fn) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        int appended = 0;
        while (matcher.find()) {
            sb.append(input, appended, matcher.start());
            sb.append(fn.apply(matcher));
            appended = matcher.end();
        }
        sb.append(input, appended, input.length());
        return sb.toString();
    }
}
