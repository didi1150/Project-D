package dev.bukkit.game.boss.pillar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class PillarShapeTest {

    @Test
    void buildsOnlyThreeByThreeShaft() {
        List<PillarShape.Offset> offsets = PillarShape.offsets(12);
        assertEquals(108, offsets.size());
        assertEquals(108, new HashSet<>(offsets).size());
    }

    @Test
    void clampsHeightToAtLeastOneLayer() {
        List<PillarShape.Offset> offsets = PillarShape.offsets(0);
        assertEquals(9, offsets.size());
    }

    @Test
    void keepsShaftWithinOneBlockRadius() {
        for (PillarShape.Offset offset : PillarShape.offsets(4)) {
            assertTrue(Math.abs(offset.x()) <= 1);
            assertTrue(Math.abs(offset.z()) <= 1);
        }
    }
}
