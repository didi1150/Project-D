package dev.core.item.display;

import java.util.EnumSet;
import java.util.Set;

public record TextStyle(TextColor color, Set<TextFormatter> formatters) {
	public static TextStyle defaultStyle(TextColor defaultColor) {
		return new TextStyle(defaultColor, EnumSet.noneOf(TextFormatter.class));
	}

	public TextStyle withColor(TextColor newColor) {
		return new TextStyle(newColor, formatters);
	}

	public TextStyle withFormatter(TextFormatter formatter) {
		Set<TextFormatter> newFormatters = EnumSet.copyOf(formatters);
		newFormatters.add(formatter);
		return new TextStyle(color, newFormatters);
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
