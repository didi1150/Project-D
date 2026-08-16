package dev.bukkit.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import dev.core.item.ItemType;
import dev.core.item.RPGItem;
import dev.core.item.loader.RPGItemRegistry;

/**
 * Unlimited-arrows groundwork: while a player holds a bow that requires arrows
 * ({@code itemType: BOW} + {@code requiresArrows: true}), a plain arrow stack
 * is kept in one of the main-inventory slots (9-35) so vanilla shooting never
 * runs dry. Free slots are preferred; when the main inventory is full the
 * highest-index occupied slot is temporarily overridden and its item is
 * stored, to be restored as soon as the player stops holding the bow.
 *
 * The stored item is only ever touched on bow-mode exit ({@link #onSlotSwap},
 * {@link #onSwapHands}, {@link #onCleanup}) - shooting merely refreshes the
 * arrow stack. Bows that fire without arrows (e.g. the Bonemerang,
 * {@code requiresArrows: false}) never arm the mechanic.
 */
public final class BowArrowManager {

    public static final int ARROW_COUNT = 64;

    /** Main-inventory slot range; hotbar (0-8), armor (36-39) and offhand (40) are excluded. */
    private static final int SLOT_START = 9;
    private static final int SLOT_END = 35;

    private static final ItemStack ARROWS = new ItemStack(Material.ARROW, ARROW_COUNT);

    private static final Map<UUID, BowState> BOWING = new HashMap<>();

    private BowArrowManager() {
    }

    private record BowState(int arrowSlot, ItemStack stored, boolean overrode) {
    }

    public static boolean isBowing(Player player) {
        return player != null && BOWING.containsKey(player.getUniqueId());
    }

    /**
     * Called when the player selects a hotbar slot: enter/exit bow mode based
     * on the item now held in hand.
     */
    public static void onSlotSwap(Player player, int newSlot) {
        ItemStack held = player.getInventory().getItem(newSlot);
        if (isBowing(player)) {
            if (!isBowRequiringArrows(held)) {
                exitBowMode(player);
            }
            return;
        }
        if (isBowRequiringArrows(held)) {
            enterBowMode(player);
        }
    }

    /**
     * Called from a deferred task when the player swaps hands: re-evaluates
     * bow mode against the (already swapped) main-hand item.
     */
    public static void onSwapHands(Player player) {
        if (isBowRequiringArrows(player.getInventory().getItemInMainHand())) {
            if (!isBowing(player)) {
                enterBowMode(player);
            }
        } else {
            exitBowMode(player);
        }
    }

    /**
     * Called after the player fires a bow: tops the arrow stack back up.
     * Never touches the stored item.
     */
    public static void onShoot(Player player) {
        BowState state = BOWING.get(player.getUniqueId());
        if (state != null) {
            player.getInventory().setItem(state.arrowSlot(), ARROWS.clone());
        }
    }

    /**
     * Restores the player's inventory to its pre-bow state (quit / death) so
     * the server never persists the planted arrow stack.
     */
    public static void onCleanup(Player player) {
        exitBowMode(player);
    }

    private static void enterBowMode(Player player) {
        int arrowSlot = firstFreeMainSlot(player);
        ItemStack stored = null;
        boolean overrode = false;
        if (arrowSlot < 0) {
            arrowSlot = lastOccupiedMainSlot(player);
            stored = player.getInventory().getItem(arrowSlot);
            overrode = true;
        }
        player.getInventory().setItem(arrowSlot, ARROWS.clone());
        BOWING.put(player.getUniqueId(), new BowState(arrowSlot, stored, overrode));
    }

    private static void exitBowMode(Player player) {
        BowState state = BOWING.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (state.overrode() && state.stored() != null) {
            player.getInventory().setItem(state.arrowSlot(), state.stored());
        } else {
            player.getInventory().setItem(state.arrowSlot(), null);
        }
    }

    private static boolean isBowRequiringArrows(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        String id = BukkitItemStackAdapter.getRpgItemId(stack);
        if (id == null) {
            return false;
        }
        Optional<RPGItem> item = RPGItemRegistry.getInstance().getItem(id);
        return item.isPresent() && item.get().getItemType() == ItemType.BOW && item.get().requiresArrows();
    }

    private static int firstFreeMainSlot(Player player) {
        Inventory inv = player.getInventory();
        for (int slot = SLOT_START; slot <= SLOT_END; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }

    private static int lastOccupiedMainSlot(Player player) {
        Inventory inv = player.getInventory();
        for (int slot = SLOT_END; slot >= SLOT_START; slot--) {
            ItemStack stack = inv.getItem(slot);
            if (stack != null && stack.getType() != Material.AIR) {
                return slot;
            }
        }
        return -1;
    }
}