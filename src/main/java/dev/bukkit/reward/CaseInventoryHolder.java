package dev.bukkit.reward;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Identity marker for reward-case inventories. Using a holder instead of the
 * title prevents collisions with other inventories and keeps click handling
 * robust if the title is changed later.
 */
public final class CaseInventoryHolder implements InventoryHolder {

    private final UUID playerId;
    private Inventory inventory;

    public CaseInventoryHolder(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
