package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * The Utility Staff's two manual clicks. RIGHT_CLICK uses the staff's current
 * mode (heal / shield / pushback); SHIFT cycles through the modes. Both are
 * registered in {@code AbilityRegistry} and configured in {@code abilities.yml}.
 * All of the actual behavior (targeting, cooldown management, mode state,
 * visual indicators) lives in the matching Bukkit effects and the per-holder
 * {@code SupportStaffBehavior}.
 */
public class SupportStaffAbility extends Ability {

	public static final String USE_ID = "STAFF_USE";
	public static final String TOGGLE_ID = "STAFF_TOGGLE";

	private SupportStaffAbility(String id) {
		super(id);
	}

	/** The right-click use half of the staff. */
	public static SupportStaffAbility use() {
		return new SupportStaffAbility(USE_ID);
	}

	/** The shift-click toggle half of the staff. */
	public static SupportStaffAbility toggle() {
		return new SupportStaffAbility(TOGGLE_ID);
	}

	/** Whether this instance backs the right-click use action. */
	public boolean isUse() {
		return USE_ID.equals(getId());
	}

	/** Whether this instance backs the shift-click toggle action. */
	public boolean isToggle() {
		return TOGGLE_ID.equals(getId());
	}
}
