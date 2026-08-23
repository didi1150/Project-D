package dev.core.stat.descriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.core.item.display.TextColor;

public class StatColorTest {

    @Test
    void parsesHexForms() {
        StatColor hash = StatColor.fromString("#12ABcd");
        assertTrue(hash.isCustom());
        assertEquals("#12ABCD", hash.getHex());

        StatColor zeroX = StatColor.fromString("0x12abcd");
        assertTrue(zeroX.isCustom());
        assertEquals("#12ABCD", zeroX.getHex());

        StatColor amp = StatColor.fromString("&#12abcd");
        assertTrue(amp.isCustom());
        assertEquals("#12ABCD", amp.getHex());
    }

    @Test
    void parsesRgbFunction() {
        StatColor rgb = StatColor.fromString("rgb(255, 128, 0)");
        assertTrue(rgb.isCustom());
        assertEquals("#FF8000", rgb.getHex());

        StatColor ampRgb = StatColor.fromString("&rgb(0,255,0)");
        assertTrue(ampRgb.isCustom());
        assertEquals("#00FF00", ampRgb.getHex());

        // components clamp to 0-255
        assertEquals("#FF0000", StatColor.fromString("rgb(999, -3, 0)").getHex());
    }

    @Test
    void parsesNamedColors() {
        StatColor named = StatColor.fromString("gold");
        assertFalse(named.isCustom());
        assertEquals(TextColor.GOLD, named.getNamed());
        assertEquals("#FFD700", named.toHex());

        StatColor upper = StatColor.fromString("DARK_PURPLE");
        assertFalse(upper.isCustom());
        assertEquals(TextColor.DARK_PURPLE, upper.getNamed());
    }

    @Test
    void parsesLegacyCodes() {
        StatColor code = StatColor.fromString("&c");
        assertFalse(code.isCustom());
        assertEquals(TextColor.RED, code.getNamed());

        StatColor sectionCode = StatColor.fromString("\u00A7a");
        assertFalse(sectionCode.isCustom());
        assertEquals(TextColor.GREEN, sectionCode.getNamed());

        StatColor digitCode = StatColor.fromString("&6");
        assertEquals(TextColor.GOLD, digitCode.getNamed());
    }

    @Test
    void invalidValuesReturnNull() {
        assertNull(StatColor.fromString(null));
        assertNull(StatColor.fromString("   "));
        assertNull(StatColor.fromString("#12345"));
        assertNull(StatColor.fromString("not-a-color"));
        assertNull(StatColor.fromString("rgb(a,b,c)"));
    }
}
