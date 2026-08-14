package dev.bukkit.utils;

import dev.core.ability.passive.SetPassive;
import dev.core.entity.RPGEntity;

/**
 * Mage set passive ("MANA_DISCOUNT"): while the full mage set is worn, every
 * ability cast costs 10% less mana. Applied at cast time where the effect
 * manager checks and deducts ability costs, and queried via
 * {@code EquipmentManager.hasSetPassive("MANA_DISCOUNT")} so breaking the set
 * immediately restores full prices. Only mana costs are discounted; other
 * resource costs (e.g. health) pass through untouched.
 */
public class ManaDiscountUtils {

    public static final String PASSIVE_ID = "MANA_DISCOUNT";
    /** Mana cost resource key (see {@code AbilityCost.manaCost}). */
    private static final String MANA_RESOURCE = "MANA_RESOURCE";
    /** 10% off mana costs. */
    public static final double MANA_DISCOUNT = 0.10;

    private ManaDiscountUtils() {
    }

    /** Marker so the registry can resolve the passive id from config. */
    public static final SetPassive MARKER = new SetPassive() {
        @Override
        public String getId() {
            return PASSIVE_ID;
        }
    };

    /**
     * The cost the caster actually pays for the given resource: mana costs are
     * reduced by 10% while the mage set passive is active; every other
     * resource passes through at full price.
     */
    public static double discountedCost(RPGEntity caster, String resourceType, double amount) {
        if (!MANA_RESOURCE.equals(resourceType) || amount <= 0) {
            return amount;
        }
        if (caster == null || !caster.getEquipmentManager().hasSetPassive(PASSIVE_ID)) {
            return amount;
        }
        return amount * (1.0 - MANA_DISCOUNT);
    }
}