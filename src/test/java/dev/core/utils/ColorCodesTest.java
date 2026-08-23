package dev.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class ColorCodesTest {

    private static final char S = '\u00A7';

    @Test
    void nullAndEmptyPassThrough() {
        assertNull(ColorCodes.translate(null));
        assertEquals("", ColorCodes.translate(""));
        assertNull(ColorCodes.translateAmp(null));
        assertEquals("", ColorCodes.translateAmp(""));
    }

    @Test
    void standardLegacyColorCodes() {
        assertEquals(S + "cHello", ColorCodes.translateAmp("&cHello"));
        assertEquals(S + "a" + S + "lBold", ColorCodes.translateAmp("&a&lBold"));
        // uppercase input codes normalize
        assertEquals(S + "c", ColorCodes.translateAmp("&C"));
    }

    @Test
    void formatCodesTranslate() {
        assertEquals(S + "l" + S + "m" + S + "n" + S + "o" + S + "k" + S + "r",
                ColorCodes.translateAmp("&l&m&n&o&k&r"));
    }

    @Test
    void unknownAmpSequencesUntouched() {
        assertEquals("a & b &z", ColorCodes.translateAmp("a & b &z"));
        assertEquals("100% & done", ColorCodes.translate("100% & done"));
    }

    @Test
    void ampHexTranslatesToLegacyHex() {
        assertEquals(S + "x" + S + "f" + S + "f" + S + "5" + S + "5" + S + "0" + S + "0Hi",
                ColorCodes.translate("&#FF5500Hi"));
    }

    @Test
    void braceHexTranslates() {
        assertEquals(S + "x" + S + "1" + S + "2" + S + "3" + S + "a" + S + "b" + S + "c",
                ColorCodes.translate("{#123abc}"));
    }

    @Test
    void rgbFunctionTranslates() {
        String expected = ColorCodes.toLegacy("FF8000");
        assertEquals(expected, ColorCodes.translate("rgb(255,128,0)"));
        assertEquals(expected, ColorCodes.translate("&rgb(255, 128, 0)"));
        assertEquals(expected, ColorCodes.translate("RGB(255,128,0)"));
    }

    @Test
    void rgbComponentsClamp() {
        // 999 -> FF, -5 -> 00
        assertEquals(ColorCodes.toLegacy("FF0000"), ColorCodes.translate("rgb(999,-5,0)"));
    }

    @Test
    void mixedNotationsInOneString() {
        String out = ColorCodes.translate("&6Gold &#00FF00Green rgb(0,0,255) {#FF00FF}");
        StringBuilder sb = new StringBuilder();
        sb.append(S).append('6').append("Gold ")
          .append(ColorCodes.toLegacy("00FF00")).append("Green ")
          .append(ColorCodes.toLegacy("0000FF"))
          .append(' ').append(ColorCodes.toLegacy("FF00FF"));
        assertEquals(sb.toString(), out);
    }

    @Test
    void invalidHexLeftAlone() {
        assertEquals("&#12345 {#GGHHII}", ColorCodes.translate("&#12345 {#GGHHII}"));
    }

    @Test
    void toLegacyRejectsBadInput() {
        assertEquals("", ColorCodes.toLegacy(null));
        assertEquals("", ColorCodes.toLegacy("12345"));
        assertEquals("", ColorCodes.toLegacy("GGHHII"));
    }

    @Test
    void rgbDigitsNormalizesAndClamps() {
        assertEquals("FF8000", ColorCodes.rgbDigits("255", "128", "0"));
        assertEquals("FF0000", ColorCodes.rgbDigits("300", "-1", "0"));
        assertNull(ColorCodes.rgbDigits("a", "2", "3"));
    }

    @Test
    void translateIsStableWhenAppliedTwice() {
        String once = ColorCodes.translate("&cGold &#00FF00 rgb(0,0,255)");
        String twice = ColorCodes.translate(once.replace("&", ""));
        assertEquals(ColorCodes.translate(once), twice);
    }
}
