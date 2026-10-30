package dev.bukkit.ability.behavior;

import net.kyori.adventure.text.format.NamedTextColor;

import dev.core.ability.ActiveAbility;
import dev.core.ability.impl.TicTacToeAbility;

/** Live target outline for the Tic Tac Toe reward item. */
public class TicTacToeTargetBehavior extends AbstractPlayerTargetGlowBehavior {

    public TicTacToeTargetBehavior(ActiveAbility ctx) {
    }

    @Override
    protected String itemId() {
        return TicTacToeAbility.ID;
    }

    @Override
    protected NamedTextColor glowColor() {
        return NamedTextColor.LIGHT_PURPLE;
    }
}
