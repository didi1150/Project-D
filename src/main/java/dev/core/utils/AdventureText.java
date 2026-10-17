package dev.core.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Bridge between the project's legacy/stylesheet strings and Adventure.
 *
 * <p>
 * All Bukkit APIs that take text now expect Adventure {@link Component}s.
 * Keeping the conversion here means config files, item lore renderers and the
 * custom style-tag parser can keep producing their existing strings while the
 * Bukkit boundary stays on the modern API.
 */
public final class AdventureText {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private AdventureText() {
    }

    /** Converts a legacy ({@code &}/{@code §}) string into a component. */
    public static @Nullable Component legacy(@Nullable String text) {
        if (text == null) {
            return null;
        }
        return LEGACY.deserialize(ColorCodes.translate(text));
    }

    /** Converts a list of legacy lines into components, preserving null. */
    public static @Nullable List<Component> legacyLines(@Nullable List<String> lines) {
        if (lines == null) {
            return null;
        }
        return lines.stream().map(AdventureText::legacy).toList();
    }

    /** Serializes a component back to a legacy string. */
    public static @Nullable String legacyString(@Nullable Component component) {
        if (component == null) {
            return null;
        }
        return LEGACY.serialize(component);
    }

    /** Serializes a list of components back to mutable legacy strings. */
    public static @Nullable List<String> legacyStrings(@Nullable List<Component> components) {
        if (components == null) {
            return null;
        }
        return components.stream().map(AdventureText::legacyString)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Extracts human-readable plain text from a component. */
    public static @Nullable String plain(@Nullable Component component) {
        if (component == null) {
            return null;
        }
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Like {@link #plain(Component)} but returns an empty string for null. */
    public static String plainOrEmpty(@Nullable Component component) {
        String plain = plain(component);
        return plain == null ? "" : plain;
    }

    /**
     * Adds an explicit non-italic decoration when the component does not already
     * specify one. Explicit italic spans are left untouched.
     */
    public static @Nullable Component withoutItalic(@Nullable Component component) {
        if (component == null) {
            return null;
        }
        if (component.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
            return component.decoration(TextDecoration.ITALIC, false);
        }
        return component;
    }

    /**
     * Converts one item-lore line. Vanilla's {@code ItemLore} merges a
     * dark-purple italic style into every line; wrapping the line in an empty
     * parent lets the line's explicit color/non-italic style win.
     */
    public static @Nullable Component itemLoreLine(@Nullable String legacyText) {
        Component line = legacy(legacyText);
        if (line == null) {
            return null;
        }
        return Component.empty().append(withoutItalic(line));
    }

    /** Converts item-lore lines using {@link #itemLoreLine(String)}. */
    public static @Nullable List<Component> itemLoreLines(@Nullable List<String> lines) {
        if (lines == null) {
            return null;
        }
        return lines.stream().map(AdventureText::itemLoreLine).toList();
    }

}
