package dev.bukkit.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HudTextLayoutTest {

    private static final int LINE_WIDTH = 300;

    @Test
    void shortTextIsOneLine() {
        assertEquals(1, HudTextLayout.lineCount("&2Utility Staff &8| &eAegis Ward", LINE_WIDTH));
    }

    @Test
    void longStaffFeedbackWraps() {
        String feedback = "&aAegis Ward: shielded Summoned Dungeon Skeleton Archer for 5s.";
        assertEquals(2, HudTextLayout.lineCount(feedback, LINE_WIDTH));
    }

    @Test
    void explicitNewlinesCountAsLines() {
        assertEquals(3, HudTextLayout.lineCount("one\ntwo\nthree", LINE_WIDTH));
    }

    @Test
    void legacyFormattingCodesDoNotAffectWidth() {
        assertEquals(HudTextLayout.lineCount("Aegis Ward", LINE_WIDTH),
                HudTextLayout.lineCount("&a&lAegis Ward", LINE_WIDTH));
    }

    @Test
    void emptyTextUsesOneLine() {
        assertEquals(1, HudTextLayout.lineCount(null, LINE_WIDTH));
        assertEquals(1, HudTextLayout.lineCount("", LINE_WIDTH));
    }

    @Test
    void widerLineWidthReducesTheLineCount() {
        String feedback = "&aAegis Ward: shielded Summoned Dungeon Skeleton Archer for 5s.";
        int narrow = HudTextLayout.lineCount(feedback, LINE_WIDTH);
        int wide = HudTextLayout.lineCount(feedback, 500);
        assertTrue(wide < narrow, "a wider line should need fewer lines");
    }
}
