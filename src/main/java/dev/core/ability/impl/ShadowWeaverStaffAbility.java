package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * The Shadow Weaver's Staff's two manual clicks. The staff is a platforming
 * tool: right-click places a shadow platform at the crosshair, left-click dashes
 * to a highlighted platform. Both are registered in {@code AbilityRegistry}
 * (see {@code preregister}) and configured in {@code abilities.yml}, which
 * supplies the trigger action and cooldown metadata. All of the actual behavior
 * (raycasting, platform lifecycle, dash animation) lives in the matching Bukkit
 * effects, which forward to {@code ShadowWeaverManager}.
 */
public class ShadowWeaverStaffAbility extends Ability {

	public static final String PLACE_ID = "SHADOW_STAFF_PLACE";
	public static final String DASH_ID = "SHADOW_STAFF_DASH";

	private ShadowWeaverStaffAbility(String id) {
		super(id);
	}

	/** The right-click placement half of the staff. */
	public static ShadowWeaverStaffAbility place() {
		return new ShadowWeaverStaffAbility(PLACE_ID);
	}

	/** The left-click dash half of the staff. */
	public static ShadowWeaverStaffAbility dash() {
		return new ShadowWeaverStaffAbility(DASH_ID);
	}

	/** Whether this instance backs the right-click placement action. */
	public boolean isPlace() {
		return PLACE_ID.equals(getId());
	}

	/** Whether this instance backs the left-click dash action. */
	public boolean isDash() {
		return DASH_ID.equals(getId());
	}
}