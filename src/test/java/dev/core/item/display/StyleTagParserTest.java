package dev.core.item.display;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.item.display.StyleTagParser.StyledSegment;
import dev.core.item.display.TextStyle.TextFormatter;

public class StyleTagParserTest {

    private StyleTagParser parser;
    private TextColor defaultColor;

    @BeforeEach
    void setUp() {
        defaultColor = TextColor.WHITE;
        parser = new StyleTagParser(defaultColor);
    }

    @Test
    void testPlainText() {
        String input = "Hello world";
        List<StyledSegment> result = parser.parse(input);

        assertEquals(1, result.size());
        assertEquals("Hello world", result.get(0).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(0).style());
    }

    @Test
    void testSingleColorTag() {
        String input = "Hello <red>world</red>!";
        List<StyledSegment> result = parser.parse(input);

        assertEquals(3, result.size());
        assertEquals("Hello ", result.get(0).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(0).style());

        assertEquals("world", result.get(1).text());
        assertEquals(TextStyle.defaultStyle(defaultColor).withColor(TextColor.RED), result.get(1).style());

        assertEquals("!", result.get(2).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(2).style());
    }

    @Test
    void testSingleFormatterTag() {
        String input = "This is <bold>important</bold> text";
        List<StyledSegment> result = parser.parse(input);

        assertEquals(3, result.size());
        assertEquals("This is ", result.get(0).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(0).style());

        assertEquals("important", result.get(1).text());
        assertEquals(TextStyle.defaultStyle(defaultColor).withFormatter(TextFormatter.BOLD), result.get(1).style());

        assertEquals(" text", result.get(2).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(2).style());
    }

    @Test
    void testNestedColorAndFormatter() {
        String input = "Deals <red><bold>double damage</bold></red> when coming back";
        List<StyledSegment> result = parser.parse(input);

        assertEquals(3, result.size());
        assertEquals("Deals ", result.get(0).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(0).style());

        assertEquals("double damage", result.get(1).text());
        TextStyle expectedStyle = TextStyle.defaultStyle(defaultColor).withColor(TextColor.RED)
                .withFormatter(TextFormatter.BOLD);
        assertEquals(expectedStyle, result.get(1).style());

        assertEquals(" when coming back", result.get(2).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(2).style());
    }

    @Test
    void testNestedFormatterAndColor() {
        String input = "This is <bold><red>nested</red></bold> text";
        List<StyledSegment> result = parser.parse(input);

        assertEquals(3, result.size());
        assertEquals("This is ", result.get(0).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(0).style());

        assertEquals("nested", result.get(1).text());
        TextStyle expectedStyle = TextStyle.defaultStyle(defaultColor).withFormatter(TextFormatter.BOLD)
                .withColor(TextColor.RED);
        assertEquals(expectedStyle, result.get(1).style());

        assertEquals(" text", result.get(2).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(2).style());
    }

    @Test
    void testMultipleFormattingTags() {
        String input = "Text with <bold><italic><underline>all formatting</underline></italic></bold> applied";
        List<StyledSegment> result = parser.parse(input);

        assertEquals(3, result.size());
        assertEquals("Text with ", result.get(0).text());

        assertEquals("all formatting", result.get(1).text());
        TextStyle expectedStyle = TextStyle.defaultStyle(defaultColor).withFormatter(TextFormatter.BOLD)
                .withFormatter(TextFormatter.ITALIC).withFormatter(TextFormatter.UNDERLINE);
        assertEquals(expectedStyle, result.get(1).style());

        assertEquals(" applied", result.get(2).text());
    }

    @Test
    void testMultipleSeparateTags() {
        String input = "Some <red>red text</red> and <blue>blue text</blue> here";
        List<StyledSegment> result = parser.parse(input);

        assertEquals(5, result.size());
        assertEquals("Some ", result.get(0).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(0).style());

        assertEquals("red text", result.get(1).text());
        assertEquals(TextStyle.defaultStyle(defaultColor).withColor(TextColor.RED), result.get(1).style());

        assertEquals(" and ", result.get(2).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(2).style());

        assertEquals("blue text", result.get(3).text());
        assertEquals(TextStyle.defaultStyle(defaultColor).withColor(TextColor.BLUE), result.get(3).style());

        assertEquals(" here", result.get(4).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(4).style());
    }

    @Test
    void testResetTag() {
        String input = "Some <red>red <reset>normal</reset> red again</red> text";
        List<StyledSegment> result = parser.parse(input);

        assertEquals(5, result.size());
        assertEquals("Some ", result.get(0).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(0).style());

        assertEquals("red ", result.get(1).text());
        assertEquals(TextStyle.defaultStyle(defaultColor).withColor(TextColor.RED), result.get(1).style());

        assertEquals("normal", result.get(2).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(2).style()); // Reset to default

        assertEquals(" red again", result.get(3).text());
        assertEquals(TextStyle.defaultStyle(defaultColor).withColor(TextColor.RED), result.get(3).style());

        assertEquals(" text", result.get(4).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(4).style());
    }

    @Test
    void testEmptyTags() {
        String input = "Text with <red></red> empty tags";
        List<StyledSegment> result = parser.parse(input);

        assertEquals(2, result.size());
        assertEquals("Text with ", result.get(0).text());
        assertEquals(" empty tags", result.get(1).text());
        // No segment for empty content
    }

    @Test
    void testInvalidTags() {
        String input = "Text with <invalidtag>content</invalidtag> here";
        List<StyledSegment> result = parser.parse(input);

        assertEquals(3, result.size());
        assertEquals("Text with ", result.get(0).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(0).style());

        assertEquals("content", result.get(1).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(1).style()); // No change for invalid tag

        assertEquals(" here", result.get(2).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(2).style());
    }

    @Test
    void testComplexNestedExample() {
        String input = "Pierces up to <yellow>10</yellow> foes.";
        List<StyledSegment> result = parser.parse(input);

        assertEquals(3, result.size());
        assertEquals("Pierces up to ", result.get(0).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(0).style());

        assertEquals("10", result.get(1).text());
        assertEquals(TextStyle.defaultStyle(defaultColor).withColor(TextColor.YELLOW), result.get(1).style());

        assertEquals(" foes.", result.get(2).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(2).style());
    }

    @Test
    void testMultilineText() {
        String input = "Line 1\n<red>Line 2</red>\nLine 3";
        List<StyledSegment> result = parser.parse(input);

        assertEquals(3, result.size());
        assertEquals("Line 1\n", result.get(0).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(0).style());

        assertEquals("Line 2", result.get(1).text());
        assertEquals(TextStyle.defaultStyle(defaultColor).withColor(TextColor.RED), result.get(1).style());

        assertEquals("\nLine 3", result.get(2).text());
        assertEquals(TextStyle.defaultStyle(defaultColor), result.get(2).style());
    }

    @Test
    void testEmptyString() {
        String input = "";
        List<StyledSegment> result = parser.parse(input);

        assertTrue(result.isEmpty());
    }

    @Test
    void testOnlyTags() {
        String input = "<red>only tagged content</red>";
        List<StyledSegment> result = parser.parse(input);

        assertEquals(1, result.size());
        assertEquals("only tagged content", result.get(0).text());
        assertEquals(TextStyle.defaultStyle(defaultColor).withColor(TextColor.RED), result.get(0).style());
    }

}
