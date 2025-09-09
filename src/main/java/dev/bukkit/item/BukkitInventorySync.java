package dev.bukkit.item;

import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.core.entity.RPGEntity;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentManager;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.item.loader.RPGItemRegistry;

public class BukkitInventorySync {

    public static void syncInventory(RPGEntity rpgEntity, Player player) {
        EquipmentManager manager = rpgEntity.getEquipmentManager();

        // Clear all current items
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            manager.unequipItem(slot);
        }
        for (RPGItem item : manager.getInventoryItems()) {
            manager.removeFromInventory(item);
        }

        // Equip armor + hands
        equipSlot(manager, EquipmentSlot.HEAD, player.getInventory().getHelmet());
        equipSlot(manager, EquipmentSlot.CHEST, player.getInventory().getChestplate());
        equipSlot(manager, EquipmentSlot.LEGS, player.getInventory().getLeggings());
        equipSlot(manager, EquipmentSlot.FEET, player.getInventory().getBoots());
        equipSlot(manager, EquipmentSlot.MAIN_HAND, player.getInventory().getItemInMainHand());
        equipSlot(manager, EquipmentSlot.OFF_HAND, player.getInventory().getItemInOffHand());

        // Add passive items
        for (ItemStack stack : player.getInventory().getContents()) {
            addPassiveIfApplicable(manager, stack);
        }
    }

    public static void updateMainHand(RPGEntity rpgEntity, Player player, int newSlot) {
        EquipmentManager manager = rpgEntity.getEquipmentManager();
        ItemStack newMainHandItem = player.getInventory().getItem(newSlot);

        RPGItem newItem = resolveRpgItem(newMainHandItem);

        manager.equipItem(EquipmentSlot.MAIN_HAND, newItem);
    }

    public static void updateMainAndOffHand(RPGEntity rpgEntity, Player player, ItemStack offHand, ItemStack mainHand) {
        EquipmentManager manager = rpgEntity.getEquipmentManager();
        manager.unequipItem(EquipmentSlot.MAIN_HAND);
        manager.unequipItem(EquipmentSlot.OFF_HAND);

        RPGItem mainItem = resolveRpgItem(mainHand);
        manager.equipItem(EquipmentSlot.MAIN_HAND, mainItem);

        RPGItem offItem = resolveRpgItem(offHand);
        manager.equipItem(EquipmentSlot.OFF_HAND, offItem);
    }

    /**
     * Diff-based sync: only updates changed slots and inventory differences.
     */
    public static void syncInventoryDiff(RPGEntity rpgEntity, Player player) {
        EquipmentManager manager = rpgEntity.getEquipmentManager();

        // Check each equipment slot
        checkSlot(manager, EquipmentSlot.HEAD, player.getInventory().getHelmet());
        checkSlot(manager, EquipmentSlot.CHEST, player.getInventory().getChestplate());
        checkSlot(manager, EquipmentSlot.LEGS, player.getInventory().getLeggings());
        checkSlot(manager, EquipmentSlot.FEET, player.getInventory().getBoots());
        checkSlot(manager, EquipmentSlot.MAIN_HAND, player.getInventory().getItemInMainHand());
        checkSlot(manager, EquipmentSlot.OFF_HAND, player.getInventory().getItemInOffHand());

        // Passive inventory items: remove those that no longer exist
        for (RPGItem passiveItem : manager.getInventoryItems()) {
            if (!containsStack(player, passiveItem)) {
                manager.removeFromInventory(passiveItem);
            }
        }

        // Add new passive items
        for (ItemStack stack : player.getInventory().getContents()) {
            addPassiveIfApplicable(manager, stack);
        }
    }

    public static void updateSlot(RPGEntity rpgEntity, EquipmentSlot slot, ItemStack newStack) {
        String newItemId = BukkitItemStackAdapter.getRpgItemId(newStack);
        RPGItem currentlyEquipped = rpgEntity.getEquipmentManager().getEquippedItem(slot);

        if (newItemId != null) {
            Optional<RPGItem> optNewItem = RPGItemRegistry.getInstance().getItem(newItemId);

            if (optNewItem.isPresent()) {
                RPGItem newItem = optNewItem.get();
                if (!newItem.equals(currentlyEquipped)) {
                    if (currentlyEquipped != null) {
                        rpgEntity.getEquipmentManager().unequipItem(slot);
                    }
                    rpgEntity.getEquipmentManager().equipItem(slot, newItem);
                }
                return;
            }
        }

        // No RPG item in this slot -> unequip if necessary
        if (currentlyEquipped != null) {
            rpgEntity.getEquipmentManager().unequipItem(slot);
        }
    }

    /**
     * Updates a slot by Bukkit slot index. 0–8: hotbar 9–35: main inventory 36–39:
     * armor (boots, leggings, chest, helmet) 40: offhand
     */
    public static void updateSlotByIndex(RPGEntity rpgEntity, Player player, int slotIndex, int previousSlot) {
        ItemStack newStack = player.getInventory().getItem(slotIndex);
        EquipmentSlot mappedSlot = mapIndexToEquipmentSlot(slotIndex, player, previousSlot);

        if (mappedSlot != null) {
            updateSlot(rpgEntity, mappedSlot, newStack);
        } else {

            // Non-equipment slots -> treat as passive inventory
            String itemId = BukkitItemStackAdapter.getRpgItemId(newStack);
            if (itemId != null) {
                RPGItemRegistry.getInstance().getItem(itemId)
                        .ifPresent(item -> rpgEntity.getEquipmentManager().addToInventory(item));
            }
        }
    }

    /**
     * Maps a Bukkit inventory slot index to an EquipmentSlot. Returns null if it's
     * not an equipment slot.
     */
    private static EquipmentSlot mapIndexToEquipmentSlot(int slotIndex, Player player, int previousSlot) {
        switch (slotIndex) {
        case 40:
            return EquipmentSlot.OFF_HAND;
        case 36:
            return EquipmentSlot.FEET;
        case 37:
            return EquipmentSlot.LEGS;
        case 38:
            return EquipmentSlot.CHEST;
        case 39:
            return EquipmentSlot.HEAD;
        default:
            // Handle main hand: Bukkit doesn't give it directly,
            // but PlayerItemHeldEvent gives the hotbar slot (0–8).
            int heldSlot = player.getInventory().getHeldItemSlot();
            if (slotIndex == heldSlot) {
                return EquipmentSlot.MAIN_HAND;
            }
            return null; // other slots are passive
        }
    }

    // ================== Helpers ==================

    private static void equipSlot(EquipmentManager manager, EquipmentSlot slot, ItemStack stack) {
        RPGItem item = resolveRpgItem(stack);
        if (item != null) {
            manager.equipItem(slot, item);
        }
    }

    private static void checkSlot(EquipmentManager manager, EquipmentSlot slot, ItemStack stack) {
        RPGItem current = manager.getEquippedItem(slot);
        RPGItem newItem = resolveRpgItem(stack);

        if (current != null && !current.equals(newItem)) {
            manager.unequipItem(slot);
        }
        if (newItem != null && !newItem.equals(current)) {
            manager.equipItem(slot, newItem);
        }
    }

    private static void addPassiveIfApplicable(EquipmentManager manager, ItemStack stack) {
        RPGItem item = resolveRpgItem(stack);
        if (item != null && !manager.isEquipped(item) && !manager.isInInventory(item)) {
            manager.addToInventory(item);
        }
    }

    private static RPGItem resolveRpgItem(ItemStack stack) {
        if (stack == null) {
            return null;
        }

        String itemId = BukkitItemStackAdapter.getRpgItemId(stack);
        if (itemId == null) {
            return null;
        }

        Optional<RPGItem> rpgItem = RPGItemRegistry.getInstance().getItem(itemId);
        return rpgItem.orElse(null);
    }

    private static boolean containsStack(Player player, RPGItem item) {
        for (ItemStack stack : player.getInventory().getContents()) {
            RPGItem resolved = resolveRpgItem(stack);
            if (resolved != null && resolved.equals(item)) {
                return true;
            }
        }
        return false;
    }
}
