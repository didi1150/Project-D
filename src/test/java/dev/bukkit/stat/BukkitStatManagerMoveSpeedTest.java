package dev.bukkit.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BukkitStatManagerMoveSpeedTest {

    @Test
    void playerBaseStillUsesPointOne() {
        assertEquals(0.1, BukkitStatManager.computeMoveSpeed(100), 0.000001);
        assertEquals(0.05, BukkitStatManager.computeMoveSpeed(50), 0.000001);
    }

    @Test
    void nativeBaseIsTheHundredPercentValue() {
        assertEquals(0.23, BukkitStatManager.computeMoveSpeed(100, 0.23), 0.000001);
        assertEquals(0.25, BukkitStatManager.computeMoveSpeed(100, 0.25), 0.000001);
        assertEquals(0.115, BukkitStatManager.computeMoveSpeed(50, 0.23), 0.000001);
    }

    @Test
    void statsAboveHundredScaleFromTheNativeBase() {
        double expected = 0.23 * (1.0 + Math.sqrt(20) / 10.0);
        assertEquals(expected, BukkitStatManager.computeMoveSpeed(120, 0.23), 0.000001);
    }
}
