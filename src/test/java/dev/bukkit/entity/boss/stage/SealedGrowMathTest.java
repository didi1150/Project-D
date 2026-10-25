package dev.bukkit.entity.boss.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SealedGrowMathTest {

    @Test
    void smoothstepEasesEndpointsAndMidpoint() {
        assertEquals(0.0, SealedGrowMath.smoothstep(0), 0.0001);
        assertEquals(1.0, SealedGrowMath.smoothstep(1), 0.0001);
        assertEquals(0.5, SealedGrowMath.smoothstep(0.5), 0.0001);
        assertEquals(0.0, SealedGrowMath.smoothstep(-1), 0.0001);
        assertEquals(1.0, SealedGrowMath.smoothstep(2), 0.0001);
    }

    @Test
    void introInterpolatesFromBuriedToHalfHeight() {
        assertEquals(0.0, SealedGrowMath.introY(0, 10, 0), 0.0001);
        assertEquals(10.0, SealedGrowMath.introY(0, 10, 1), 0.0001);
        assertEquals(5.0, SealedGrowMath.introY(0, 10, 0.5), 0.0001);
    }

    @Test
    void ascentSpeedsUpAsPillarsLoseHealth() {
        assertEquals(0.1, SealedGrowMath.ascentSpeed(0.1, 1.0, 100, 100), 0.0001);
        assertEquals(1.0, SealedGrowMath.ascentSpeed(0.1, 1.0, 0, 100), 0.0001);
        assertEquals(0.55, SealedGrowMath.ascentSpeed(0.1, 1.0, 50, 100), 0.0001);
        assertEquals(1.0, SealedGrowMath.ascentSpeed(0.1, 1.0, 0, 0), 0.0001);
    }

    @Test
    void fractionAdvancesAndClamps() {
        assertEquals(0.1, SealedGrowMath.nextFraction(0, 1, 1, 10), 0.0001);
        assertEquals(1.0, SealedGrowMath.nextFraction(0.95, 1, 1, 10), 0.0001);
        assertEquals(1.0, SealedGrowMath.nextFraction(0, 1, 1, 0), 0.0001);
    }

    @Test
    void growStateExposesStalactiteTuningPercentages() {
        BossGrowStageType.GrowState state = new BossGrowStageType.GrowState();
        assertEquals(10.0, state.getStalactitePushPercent(), 0.0001);
        assertEquals(10.0, state.getStalactiteExplosionPercent(), 0.0001);

        state.stalactitePushFraction = 0.25;
        state.stalactiteExplosionFraction = 0.15;
        assertEquals(25.0, state.getStalactitePushPercent(), 0.0001);
        assertEquals(15.0, state.getStalactiteExplosionPercent(), 0.0001);
    }

    @Test
    void growStateSupportsPushDownAndPillarDamage() {
        BossGrowStageType.GrowState state = new BossGrowStageType.GrowState();
        state.pillarMaxHp = 100;
        state.pillarCurrentHp = 100;
        state.heightFraction = 0.6;

        state.damagePillars(40);
        assertEquals(0.6, state.getPillarHealthFraction(), 0.0001);

        state.pushDown(0.25);
        assertEquals(0.35, state.getHeightFraction(), 0.0001);

        state.pushDown(1.0);
        assertEquals(0.0, state.getHeightFraction(), 0.0001);

        state.damagePillars(1000);
        assertEquals(0.0, state.getPillarHealthFraction(), 0.0001);

        state.setPillarHealthFraction(0.25);
        assertEquals(25.0, state.getPillarCurrentHp(), 0.0001);
        assertEquals(0.25, state.getPillarHealthFraction(), 0.0001);

        state.setPillarHp(75);
        assertEquals(75.0, state.getPillarCurrentHp(), 0.0001);
        assertEquals(0.75, state.getPillarHealthFraction(), 0.0001);

        state.setPillarHp(1000);
        assertEquals(100.0, state.getPillarCurrentHp(), 0.0001);
    }
}
