package dev.bukkit.ability;

/**
 * Pure per-tick state machine for {@link BukkitSmokeShroudEffect}. Extracted
 * from the effect so the aggro/reveal transitions are unit-testable without a
 * server: the effect reads live player/shroud state, feeds it in here, and
 * acts on the returned decisions.
 *
 * <ul>
 *   <li>{@link #clearAggroNow} — rising edge into SHROUDED (cast, re-entry,
 *       attack-reveal expiry) clears held targets once; the periodic slot keeps
 *       a throttled safety sweep while inside so nothing relies solely on the
 *       EntityTargetLivingEntityEvent gate between edges.</li>
 *   <li>{@link #enterCue} — first tick back inside a shrouded cloud plays the
 *       entry particles/sound.</li>
 *   <li>{@link #exitReveal} — leaving the radius opens the documented 1.5s
 *       reveal window ("attacking or leaving reveals you briefly").</li>
 * </ul>
 */
public record ShroudTickDecision(boolean clearAggroNow, boolean enterCue, boolean exitReveal) {

    /** Safety-sweep cadence in ticks while continuously shrouded inside. */
    public static final int SAFETY_CLEAR_INTERVAL_TICKS = 5;

    public static ShroudTickDecision evaluate(boolean insideRadius, boolean shrouded,
            boolean wasInside, boolean wasShrouded, boolean periodicSafetySlot) {
        boolean risingEdge = shrouded && !wasShrouded;
        boolean clearAggro = insideRadius && shrouded && (risingEdge || periodicSafetySlot);
        boolean enterCue = insideRadius && shrouded && !wasInside;
        boolean exitReveal = !insideRadius && wasInside;
        return new ShroudTickDecision(clearAggro, enterCue, exitReveal);
    }
}
