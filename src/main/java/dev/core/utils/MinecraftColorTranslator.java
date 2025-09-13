package dev.core.utils;

import java.util.HashMap;
import java.util.Map;

public class MinecraftColorTranslator {

    // ANSI reset code
    private static final String ANSI_RESET = "\u001B[0m";

    // Maps for colors and styles
    private static final Map<Character, String> COLOR_MAP = new HashMap<>();
    private static final Map<Character, String> FORMAT_MAP = new HashMap<>();

    static {
        // Colors
        COLOR_MAP.put('0', "\u001B[30m"); // Black
        COLOR_MAP.put('1', "\u001B[34m"); // Dark Blue
        COLOR_MAP.put('2', "\u001B[32m"); // Dark Green
        COLOR_MAP.put('3', "\u001B[36m"); // Dark Aqua
        COLOR_MAP.put('4', "\u001B[31m"); // Dark Red
        COLOR_MAP.put('5', "\u001B[35m"); // Dark Purple
        COLOR_MAP.put('6', "\u001B[33m"); // Gold
        COLOR_MAP.put('7', "\u001B[37m"); // Gray
        COLOR_MAP.put('8', "\u001B[90m"); // Dark Gray
        COLOR_MAP.put('9', "\u001B[94m"); // Blue
        COLOR_MAP.put('a', "\u001B[92m"); // Green
        COLOR_MAP.put('b', "\u001B[96m"); // Aqua
        COLOR_MAP.put('c', "\u001B[91m"); // Red
        COLOR_MAP.put('d', "\u001B[95m"); // Light Purple
        COLOR_MAP.put('e', "\u001B[93m"); // Yellow
        COLOR_MAP.put('f', "\u001B[97m"); // White
        COLOR_MAP.put('r', ANSI_RESET);   // Reset

        // Formatting
        FORMAT_MAP.put('l', "\u001B[1m");  // Bold
        FORMAT_MAP.put('n', "\u001B[4m");  // Underline
        FORMAT_MAP.put('o', "\u001B[3m");  // Italic
        FORMAT_MAP.put('m', "\u001B[9m");  // Strikethrough
        FORMAT_MAP.put('k', "");           // Obfuscated (not supported in console)
    }

    /**
     * Translates a Minecraft-colored/formatted string (with § codes)
     * into an ANSI-colored/formatted string for console output.
     */
    public static String translateToAnsi(String input) {
        StringBuilder sb = new StringBuilder();
        char[] chars = input.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '§' && i + 1 < chars.length) {
                char code = Character.toLowerCase(chars[i + 1]);

                if (COLOR_MAP.containsKey(code)) {
                    sb.append(COLOR_MAP.get(code));
                    i++; // skip formatting code
                    continue;
                }
                if (FORMAT_MAP.containsKey(code)) {
                    sb.append(FORMAT_MAP.get(code));
                    i++; // skip formatting code
                    continue;
                }
            }
            sb.append(chars[i]);
        }

        sb.append(ANSI_RESET); // reset at end
        return sb.toString();
    }

    // Example usage
    public static void main(String[] args) {
        String test = "§aGreen §lBold §nUnderlined §cRed §oItalic §rNormal!";
        System.out.println(translateToAnsi(test));
    }
}
