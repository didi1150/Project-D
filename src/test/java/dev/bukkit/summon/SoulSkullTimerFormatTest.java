package dev.bukkit.summon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SoulSkullTimerFormatTest {

    @Test
    void formatsSecondsAndOneTenth() {
        assertEquals("20:0", SoulSkull.formatRemaining(400));
        assertEquals("19:9", SoulSkull.formatRemaining(399));
        assertEquals("10:0", SoulSkull.formatRemaining(200));
        assertEquals("0:0", SoulSkull.formatRemaining(1));
        assertEquals("0:0", SoulSkull.formatRemaining(0));
    }
}
