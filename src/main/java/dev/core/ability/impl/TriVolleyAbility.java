package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * Trinity Bow — Scatter Volley. Left-click toggles the volley: ON arms the next
 * bow shot (cost paid), OFF cancels for free. The cooldown starts only when the
 * armed volley is actually fired. Cost/cooldown configured in abilities.yml
 * (TRI_VOLLEY).
 */
public class TriVolleyAbility extends Ability {

    public static final String ID = "TRI_VOLLEY";

    public TriVolleyAbility() {
        super(ID);
    }

    @Override
    public String getId() {
        return ID;
    }
}
