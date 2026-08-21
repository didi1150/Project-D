package dev.core.item.display;

import java.util.EnumSet;
import java.util.Set;

/**
 * A text style: an optional named {@link TextColor}, an optional custom RGB hex
 * color ({@code #RRGGBB}), and a set of formatters. Exactly one of
 * {@code color} / {@code hexColor} is non-null.
 */
public record TextStyle(TextColor color, String hexColor, Set<TextFormatter> formatters) {
	public static TextStyle defaultStyle(TextColor defaultColor) {
		return new TextStyle(defaultColor, null, EnumSet.noneOf(TextFormatter.class));
	}

	public TextStyle withColor(TextColor newColor) {
		return new TextStyle(newColor, null, formatters);
	}

	public TextStyle withHexColor(String hex) {
		return new TextStyle(null, hex, formatters);
	}

	public TextStyle withFormatter(TextFormatter formatter) {
		Set<TextFormatter> newFormatters = EnumSet.copyOf(formatters);
		newFormatters.add(formatter);
		return new TextStyle(color, hexColor, newFormatters);
	}

	public TextStyle reset(TextColor defaultColor) {
		return defaultStyle(defaultColor);
	}

	public enum TextFormatter {
		BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, RESET;

		public static TextFormatter fromString(String s) {
			try {
				return TextFormatter.valueOf(s.toUpperCase());
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
	}
}