package dev.bukkit.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * Pure-math tests for the Shadow Weaver's Staff runtime. The static helpers on
 * {@link ShadowWeaverManager} never touch a server (Material is a plain enum),
 * so these run headless.
 */
class ShadowWeaverLogicTest {

	// ---- Platform placement proximity ----------------------------------------

	@Test
	void platformThreeBlocksAwayIsAllowed() {
		List<Vector> existing = List.of(new Vector(0, 0, 0));
		assertFalse(ShadowWeaverManager.violatesProximity(existing, new Vector(3, 0, 0)));
	}

	@Test
	void platformJustUnderTwoBlocksViolates() {
		List<Vector> existing = List.of(new Vector(0, 0, 0));
		assertTrue(ShadowWeaverManager.violatesProximity(existing, new Vector(1.41, 0, 0)));
	}

	@Test
	void platformSameSpotViolates() {
		List<Vector> existing = List.of(new Vector(0, 0, 0));
		assertTrue(ShadowWeaverManager.violatesProximity(existing, new Vector(0, 0, 0)));
	}

	@Test
	void proximityChecksEveryExistingPlatform() {
		List<Vector> existing = List.of(new Vector(0, 0, 0), new Vector(10, 0, 0));
		assertTrue(ShadowWeaverManager.violatesProximity(existing, new Vector(9.5, 0, 0)));
	}

	// ---- Platform decay (green -> red) ----------------------------------------

	@Test
	void freshPlatformIsGreen() {
		assertEquals(Material.GREEN_STAINED_GLASS, ShadowWeaverManager.decayBlock(0));
	}

	@Test
	void justBeforeFirstDecayStillGreen() {
		assertEquals(Material.GREEN_STAINED_GLASS, ShadowWeaverManager.decayBlock(ShadowWeaverManager.DECAY_LIME_MS - 1));
	}

	@Test
	void atFirstDecayThresholdBecomesLime() {
		assertEquals(Material.LIME_STAINED_GLASS, ShadowWeaverManager.decayBlock(ShadowWeaverManager.DECAY_LIME_MS));
	}

	@Test
	void midLifeBecomesYellow() {
		assertEquals(Material.YELLOW_STAINED_GLASS, ShadowWeaverManager.decayBlock(ShadowWeaverManager.DECAY_YELLOW_MS));
	}

	@Test
	void lateLifeBecomesOrange() {
		assertEquals(Material.ORANGE_STAINED_GLASS, ShadowWeaverManager.decayBlock(ShadowWeaverManager.DECAY_ORANGE_MS));
	}

	@Test
	void nearExpiryBecomesRed() {
		assertEquals(Material.RED_STAINED_GLASS, ShadowWeaverManager.decayBlock(ShadowWeaverManager.DECAY_RED_MS));
	}

	@Test
	void atFullLifetimeStillRed() {
		assertEquals(Material.RED_STAINED_GLASS, ShadowWeaverManager.decayBlock(ShadowWeaverManager.PLATFORM_LIFETIME_MS));
	}

	// ---- Platform colour interpolation -----------------------------------------

	@Test
	void freshPlatformColourIsPureGreen() {
		Color color = ShadowWeaverManager.platformColor(0);
		assertEquals(0, color.getRed());
		assertEquals(255, color.getGreen());
		assertEquals(0, color.getBlue());
	}

	@Test
	void halfLifePlatformColourIsPureYellow() {
		Color color = ShadowWeaverManager.platformColor(ShadowWeaverManager.PLATFORM_LIFETIME_MS / 2);
		assertEquals(255, color.getRed());
		assertEquals(255, color.getGreen());
		assertEquals(0, color.getBlue());
	}

	@Test
	void expiredPlatformColourIsPureRed() {
		Color color = ShadowWeaverManager.platformColor(ShadowWeaverManager.PLATFORM_LIFETIME_MS);
		assertEquals(255, color.getRed());
		assertEquals(0, color.getGreen());
		assertEquals(0, color.getBlue());
	}

	@Test
	void platformColourNeverGoesPastExpiry() {
		Color color = ShadowWeaverManager.platformColor(ShadowWeaverManager.PLATFORM_LIFETIME_MS * 10);
		assertEquals(255, color.getRed());
		assertEquals(0, color.getGreen());
		assertEquals(0, color.getBlue());
	}

	// ---- Dash range ------------------------------------------------------------

	@Test
	void exactlyAtMinimumRangeIsInside() {
		double minSq = ShadowWeaverManager.DASH_RANGE_MIN * ShadowWeaverManager.DASH_RANGE_MIN;
		assertTrue(ShadowWeaverManager.insideDashRange(minSq));
	}

	@Test
	void justBelowMinimumRangeIsOutside() {
		double minSq = ShadowWeaverManager.DASH_RANGE_MIN * ShadowWeaverManager.DASH_RANGE_MIN;
		assertFalse(ShadowWeaverManager.insideDashRange(minSq - 0.01));
	}

	@Test
	void exactlyAtMaximumRangeIsInside() {
		double maxSq = ShadowWeaverManager.DASH_RANGE_MAX * ShadowWeaverManager.DASH_RANGE_MAX;
		assertTrue(ShadowWeaverManager.insideDashRange(maxSq));
	}

	@Test
	void justAboveMaximumRangeIsOutside() {
		double maxSq = ShadowWeaverManager.DASH_RANGE_MAX * ShadowWeaverManager.DASH_RANGE_MAX;
		assertFalse(ShadowWeaverManager.insideDashRange(maxSq + 0.01));
	}

	@Test
	void negativeDistanceIsOutside() {
		assertFalse(ShadowWeaverManager.insideDashRange(-1.0));
	}

	// ---- Crosshair lock alignment ----------------------------------------------

	@Test
	void perfectlyAlignedLocks() {
		assertTrue(ShadowWeaverManager.isAimingAt(new Vector(0, 0, -1), new Vector(0, 0, -3),
				ShadowWeaverManager.AIM_DOT_THRESHOLD));
	}

	@Test
	void perpendicularNeverLocks() {
		assertFalse(ShadowWeaverManager.isAimingAt(new Vector(0, 0, -1), new Vector(3, 0, 0),
				ShadowWeaverManager.AIM_DOT_THRESHOLD));
	}

	@Test
	void tinyOffAngleStillLocks() {
		// ~2 degrees off the axis: cos ~= 0.9994 > 0.995.
		Vector look = new Vector(0, 0, -1);
		Vector toPlatform = new Vector(Math.sin(Math.toRadians(2)) * 10, 0, -Math.cos(Math.toRadians(2)) * 10);
		assertTrue(ShadowWeaverManager.isAimingAt(look, toPlatform, ShadowWeaverManager.AIM_DOT_THRESHOLD));
	}

	@Test
	void eightDegreesOffDoesNotLock() {
		// ~8 degrees off the axis: cos ~= 0.990 < 0.995.
		Vector look = new Vector(0, 0, -1);
		Vector toPlatform = new Vector(Math.sin(Math.toRadians(8)) * 10, 0, -Math.cos(Math.toRadians(8)) * 10);
		assertFalse(ShadowWeaverManager.isAimingAt(look, toPlatform, ShadowWeaverManager.AIM_DOT_THRESHOLD));
	}

	// ---- Dash interpolation -----------------------------------------------------

	@Test
	void dashStartsAtFirstStep() {
		assertEquals(0.25, ShadowWeaverManager.dashStep(0, ShadowWeaverManager.DASH_TICKS));
	}

	@Test
	void dashProgressMonotonicallyIncreases() {
		double previous = -1;
		for (int step = 0; step < ShadowWeaverManager.DASH_TICKS; step++) {
			double progress = ShadowWeaverManager.dashStep(step, ShadowWeaverManager.DASH_TICKS);
			assertTrue(progress > previous);
			previous = progress;
		}
	}

	@Test
	void dashReachesArrivalAtFinalTick() {
		assertEquals(1.0, ShadowWeaverManager.dashStep(ShadowWeaverManager.DASH_TICKS - 1, ShadowWeaverManager.DASH_TICKS));
	}
}