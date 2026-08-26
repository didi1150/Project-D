package dev.bukkit.ability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure state machine behind {@link BukkitSmokeShroudEffect#tick}: the rising
 * edge into SHROUDED clears aggro once, the periodic slot sweeps as a safety
 * net, and leaving the radius opens the documented reveal window.
 */
class SmokeShroudTickDecisionTest {

    @Test
    void castTickClearsAggroAndPlaysEnterCue() {
        // first tick after cast: player inside, shrouded, no prior state
        ShroudTickDecision d = ShroudTickDecision.evaluate(true, true, false, false, false);

        assertTrue(d.clearAggroNow(), "cast must drop mobs already targeting the caster");
        assertTrue(d.enterCue());
        assertFalse(d.exitReveal());
    }

    @Test
    void steadyShroudedTickDoesNothingWithoutSafetySlot() {
        ShroudTickDecision d = ShroudTickDecision.evaluate(true, true, true, true, false);

        assertFalse(d.clearAggroNow(), "edge-driven: no re-scan on quiet ticks");
        assertFalse(d.enterCue());
        assertFalse(d.exitReveal());
    }

    @Test
    void safetySlotSweepsWhileContinuouslyShrouded() {
        ShroudTickDecision d = ShroudTickDecision.evaluate(true, true, true, true, true);

        assertTrue(d.clearAggroNow(), "periodic slot is the backstop for slipped-through targets");
        assertFalse(d.enterCue());
    }

    @Test
    void attackRevealExpiryWhileStayingInsideClearsAggro() {
        // wasShrouded=false because a swing opened the reveal window; it just expired
        ShroudTickDecision d = ShroudTickDecision.evaluate(true, true, true, false, false);

        assertTrue(d.clearAggroNow(), "the whole point: aggro drops once the reveal ends");
        assertFalse(d.enterCue(), "no entry cue — never left the cloud");
    }

    @Test
    void leavingTheCloudTriggersRevealOnly() {
        ShroudTickDecision d = ShroudTickDecision.evaluate(false, false, true, true, false);

        assertFalse(d.clearAggroNow());
        assertFalse(d.enterCue());
        assertTrue(d.exitReveal(), "documented penalty: attacking or leaving reveals briefly");
    }

    @Test
    void beingOutsideIsQuiet() {
        ShroudTickDecision d = ShroudTickDecision.evaluate(false, false, false, false, true);

        assertFalse(d.clearAggroNow(), "no protection to enforce while outside");
        assertFalse(d.exitReveal(), "exit reveal fires once per exit transition only");
    }

    @Test
    void revealedInsideTickKeepsQuiet() {
        // inside but revealed (shrouded=false), still was inside before
        ShroudTickDecision d = ShroudTickDecision.evaluate(true, false, true, true, false);

        assertFalse(d.clearAggroNow(), "revealed means targetable — no clearing mid-window");
        assertFalse(d.exitReveal());
    }
}
