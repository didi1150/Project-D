package dev.bukkit.ability.behavior;

import net.kyori.adventure.text.format.NamedTextColor;

import dev.core.ability.ActiveAbility;
import dev.core.ability.impl.TeleportationStaffAbility;

/** Live target outline for the Teleportation Staff reward item. */
public class TeleportationStaffTargetBehavior extends AbstractPlayerTargetGlowBehavior {

    public TeleportationStaffTargetBehavior(ActiveAbility ctx) {
    }

    @Override
    protected String itemId() {
        return TeleportationStaffAbility.ID;
    }

    @Override
    protected NamedTextColor glowColor() {
        return NamedTextColor.AQUA;
    }
}
