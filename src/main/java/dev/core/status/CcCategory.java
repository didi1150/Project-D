package dev.core.status;

/**
 * Crowd-control severity class. Drives the stacking/override rules in
 * {@link StatusEffectManager}:
 *
 * <ul>
 * <li>{@code SOFT} (slowed, rooted) restricts movement only; attackers can
 * still attack and cast. Multiple different soft CCs coexist.</li>
 * <li>{@code HARD} (stunned, airborne) is full control loss; applying one
 * clears all soft CC and only one hard CC can be active at a time.</li>
 * <li>{@code IMMUNITY} (CC immune) cleanses every active CC on apply and
 * rejects all other CC while active.</li>
 * </ul>
 */
public enum CcCategory {
	SOFT, HARD, IMMUNITY
}