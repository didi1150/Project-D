package dev.core.ability.impl;

import dev.core.ability.Ability;

/**
 * Trinity Bow — Scatter Volley. Left-click fans 5 piercing arrows.
 * Cost/cooldown configured in abilities.yml (TRI_VOLLEY).
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
