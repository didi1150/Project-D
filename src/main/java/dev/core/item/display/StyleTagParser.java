package dev.core.item.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.core.item.display.TextStyle.TextFormatter;
import dev.core.stat.descriptor.StatColor;
import dev.core.stat.descriptor.StatDescriptor;
import dev.core.stat.descriptor.StatRegistry;

/**
 * Parses styled text into {@link StyledSegment}s. Supports:
 * <ul>
 * <li>named color tags: {@code <red>...</red>}</li>
 * <li>formatter tags: {@code <bold>...</bold>}, {@code <reset>...</reset>}</li>
 * <li>self-closing style switches: {@code <gray/>}, {@code <bold/>} — apply the
 * tag's style to everything that follows (until the next tag or end of line)
 * without wrapping text</li>
 * <li>stat placeholders: {@code <stat:ID>} (stat name) and
 * {@code <stat:ID:AMT>} (formatted value, e.g. {@code <stat:core:attack_damage:150>}
 * renders as "&#9874; +150 Attack Damage" colored by the stat metadata)</li>
 * </ul>
 */
public class StyleTagParser {

    private static final Pattern TAG_PATTERN = Pattern.compile("<(\\w+)>(.*?)</\\1>", Pattern.DOTALL);
    private static final Pattern SELF_CLOSING_TAG_PATTERN = Pattern.compile("<(\\w+)\\s*/>");
    private static final Pattern STAT_TAG_PATTERN = Pattern
            .compile("<(?:stat|STAT):([a-zA-Z0-9_:-]+?)(?::(-?\\d+(?:\\.\\d+)?))?>");

    private final TextColor defaultColor;

    public StyleTagParser(TextColor defaultColor) {
        this.defaultColor = defaultColor;
    }

    public List<StyledSegment> parse(String line) {
        return parseInternal(line, TextStyle.defaultStyle(defaultColor));
    }

    private List<StyledSegment> parseInternal(String input, TextStyle currentStyle) {
        List<StyledSegment> segments = new ArrayList<>();
        Matcher matcher = TAG_PATTERN.matcher(input);
        Matcher selfClosingMatcher = SELF_CLOSING_TAG_PATTERN.matcher(input);
        Matcher statMatcher = STAT_TAG_PATTERN.matcher(input);

        int lastEnd = 0;
        boolean pairFound = matcher.find();
        boolean selfClosingFound = selfClosingMatcher.find();
        boolean statFound = statMatcher.find();
        while (pairFound || selfClosingFound || statFound) {
            // Earliest match wins; ties impossible since the patterns are
            // mutually exclusive (self-closing requires '/>' right after the
            // name, paired tags require '>', stat tags require ':').
            int nextStart = pairFound ? matcher.start() : Integer.MAX_VALUE;
            if (selfClosingFound && selfClosingMatcher.start() < nextStart) {
                nextStart = selfClosingMatcher.start();
            }
            if (statFound && statMatcher.start() < nextStart) {
                nextStart = statMatcher.start();
            }

            // Add plain text before the tag with current style
            if (nextStart > lastEnd) {
                segments.add(new StyledSegment(input.substring(lastEnd, nextStart), currentStyle));
            }

            if (selfClosingFound && selfClosingMatcher.start() == nextStart) {
                currentStyle = calculateTagStyle(selfClosingMatcher.group(1), currentStyle);
                lastEnd = selfClosingMatcher.end();
                selfClosingFound = selfClosingMatcher.find();
            } else if (pairFound && matcher.start() == nextStart) {
                String tag = matcher.group(1);
                String inner = matcher.group(2);

                // Calculate the new style for this tag
                TextStyle tagStyle = calculateTagStyle(tag, currentStyle);

                // Recursively parse the inner content with the new style
                segments.addAll(parseInternal(inner, tagStyle));

                lastEnd = matcher.end();
                pairFound = matcher.find();
                // Any stat or self-closing tokens inside the consumed pair
                // were already parsed by the recursion; keep both matchers
                // past this region so they are not applied twice.
                statMatcher.region(lastEnd, input.length());
                statFound = statMatcher.find();
                selfClosingMatcher.region(lastEnd, input.length());
                selfClosingFound = selfClosingMatcher.find();
            } else {
                StyledSegment statSegment = buildStatSegment(statMatcher.group(1), statMatcher.group(2));
                if (statSegment != null) {
                    segments.add(statSegment);
                } else {
                    // Unknown stat id: keep the literal token so misconfigurations show
                    segments.add(new StyledSegment(statMatcher.group(), currentStyle));
                }
                lastEnd = statMatcher.end();
                statFound = statMatcher.find();
            }
        }

        // Add any remaining plain text
        if (lastEnd < input.length()) {
            segments.add(new StyledSegment(input.substring(lastEnd), currentStyle));
        }

        return segments;
    }

    /**
     * Resolves a {@code <stat:ID[:AMT]>} token to its display segment, or
     * {@code null} when the stat id is not registered. Accepts both fully
     * qualified ids ("core:attack_damage") and short names ("attack_damage").
     */
    private StyledSegment buildStatSegment(String statId, String amountRaw) {
        StatDescriptor descriptor = resolve(statId);
        if (descriptor == null) {
            return null;
        }
        TextStyle style = statStyle(descriptor.getColor());
        if (amountRaw == null) {
            return new StyledSegment(descriptor.getFormattedName(), style);
        }
        double amount = Double.parseDouble(amountRaw);
        return new StyledSegment(descriptor.formatValue(amount, true), style);
    }

    private StatDescriptor resolve(String statId) {
        Optional<StatDescriptor> direct = StatRegistry.getInstance().get(statId);
        if (direct.isPresent()) {
            return direct.get();
        }
        if (statId.indexOf(':') < 0) {
            return StatRegistry.getInstance().get("core:" + statId).orElse(null);
        }
        return null;
    }

    /**
     * Style for a stat's color: custom hex colors render as hex, named colors as
     * legacy codes.
     */
    private TextStyle statStyle(StatColor color) {
        if (color.isCustom()) {
            return TextStyle.defaultStyle(TextColor.WHITE).withHexColor(color.getHex());
        }
        return TextStyle.defaultStyle(color.getNamed() != null ? color.getNamed() : defaultColor);
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