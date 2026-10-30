package dev.bukkit.reward.special;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Identity marker for a Tic Tac Toe game inventory. */
public final class TicTacToeInventoryHolder implements InventoryHolder {

    private final UUID gameId;
    private Inventory inventory;

    public TicTacToeInventoryHolder(UUID gameId) {
        this.gameId = gameId;
    }

    public UUID getGameId() {
        return gameId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
