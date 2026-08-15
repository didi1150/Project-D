package dev.bukkit.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.bukkit.item.BukkitItemStackAdapter;
import dev.core.entity.EntityManager;
import dev.core.item.RPGItem;
import dev.core.item.loader.RPGItemRegistry;

public class BukkitPlayerInventoryUpdater {

    private final Player player;
    private final Map<String, Long> updateTimeStamps = new HashMap<>();
    private final long checkInterval; // ticks
    private long ticksCount = 0;

    public BukkitPlayerInventoryUpdater(Player player, long checkIntervalTicks) {
        this.player = player;
        this.checkInterval = checkIntervalTicks;
    }

    public void tick() {
        ticksCount++;
        if (ticksCount % checkInterval != 0) {
            return;
        }

        if (ticksCount >= 100) {
            ticksCount = 0;
        }

        int amountOfUpdates = 0;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null) {
                continue;
            }

//            if (!needsUpdate(stack)) {
//                continue;
//            }

            updateItem(slot, stack);
            amountOfUpdates++;
        }
        System.out.println("[Inventory Updater] Updated " + amountOfUpdates + " items");
    }

//    private boolean needsUpdate(ItemStack stack) {
//        String rpgId = BukkitItemStackAdapter.getRpgItemId(stack);
//        if (rpgId == null) {
//            return false;
//        }
//
//        Optional<RPGItem> optItem = RPGItemRegistry.getInstance().getItem(rpgId);
//        if (optItem.isEmpty()) {
//            return false;
//        }
//
//        long definitionTs = BukkitItemStackAdapter.getUpdateableTimestamp(); // timestamp from config/loader
//        long lastUpdate = updateTimeStamps.getOrDefault(rpgId, -1L);
//
//        // if never updated, or outdated → needs update
//        return lastUpdate < definitionTs;
//    }

    private void updateItem(int slot, ItemStack oldStack) {
        String rpgId = BukkitItemStackAdapter.getRpgItemId(oldStack);
        Optional<RPGItem> item = RPGItemRegistry.getInstance().getItem(rpgId);
        if (item.isEmpty()) {
            return;
        }

        ItemStack newStack = BukkitItemStackAdapter.toItemStack(item.get(),
                EntityManager.getInstance().getEntity(player.getUniqueId()).orElse(null));
        // getItemMeta() returns a fresh copy per call: mutate ONE meta and write
        // it back once, or the uuid copy targets a discarded copy and the fresh
        // random uuid from toItemStack() sticks (re-keying ITEM-scoped effects).
        ItemMeta newMeta = newStack.getItemMeta();
        oldStack.getItemMeta().getPersistentDataContainer().copyTo(newMeta.getPersistentDataContainer(), true);
        newStack.setItemMeta(newMeta);

        player.getInventory().setItem(slot, newStack);
    }

    public void setUpdateTimeStamp(UUID uuid, long updateTimeStamp) {
        updateTimeStamps.put(uuid.toString(), updateTimeStamp);
    }

    public void removeUpdateTimeStamp(UUID uuid) {
        if (updateTimeStamps.containsKey(uuid.toString())) {
            updateTimeStamps.remove(uuid.toString());
        }
    }
}
