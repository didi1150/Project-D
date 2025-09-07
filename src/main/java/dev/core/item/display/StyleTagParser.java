package dev.core.item.display;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.core.item.display.TextStyle.TextFormatter;

public class StyleTagParser {
    private final TextColor defaultColor;

    public StyleTagParser(TextColor defaultColor) {
        this.defaultColor = defaultColor;
    }

    public List<StyledSegment> parse(String line) {
        return parseInternal(line, TextStyle.defaultStyle(defaultColor));
    }

    private List<StyledSegment> parseInternal(String input, TextStyle currentStyle) {
        List<StyledSegment> segments = new ArrayList<>();
        Pattern pattern = Pattern.compile("<(\\w+)>(.*?)</\\1>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input);

        int lastEnd = 0;
        while (matcher.find()) {
            // Add plain text before the tag with current style
            if (matcher.start() > lastEnd) {
                segments.add(new StyledSegment(input.substring(lastEnd, matcher.start()), currentStyle));
            }

            String tag = matcher.group(1);
            String inner = matcher.group(2);

            // Calculate the new style for this tag
            TextStyle tagStyle = calculateTagStyle(tag, currentStyle);

            // Recursively parse the inner content with the new style
            segments.addAll(parseInternal(inner, tagStyle));

            lastEnd = matcher.end();
        }

        // Add any remaining plain text
        if (lastEnd < input.length()) {
            segments.add(new StyledSegment(input.substring(lastEnd), currentStyle));
        }

        return segments;
    }

    private TextStyle calculateTagStyle(String tag, TextStyle baseStyle) {
        // Handle color tags
        TextColor color = TextColor.fromString(tag);
        if (color != null) {
            return baseStyle.withColor(color);
        }

        // Handle formatter tags
        TextFormatter formatter = TextFormatter.fromString(tag);
        if (formatter != null) {
            if (formatter == TextFormatter.RESET) {
                return baseStyle.reset(defaultColor);
            } else {
                return baseStyle.withFormatter(formatter);
            }
        }

        // If tag is not recognized, return the base style unchanged
        return baseStyle;
    }

    public record StyledSegment(String text, TextStyle style) {
    }
}