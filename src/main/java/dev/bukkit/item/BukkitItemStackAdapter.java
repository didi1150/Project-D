package dev.bukkit.item;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import dev.bukkit.item.display.BukkitLoreRenderer;
import dev.bukkit.item.display.BukkitTextColorAdapter;
import dev.core.item.RPGItem;
import dev.core.item.display.RPGItemLoreRenderer;
import dev.core.item.display.TextColor;

public class BukkitItemStackAdapter {

    private static final NamespacedKey ITEM_ID_KEY = new NamespacedKey("project_d", "rpgitem_id");
    public static final NamespacedKey UUID_ID_KEY = new NamespacedKey("project_d", "uuid");
    public static final NamespacedKey UPDATEABLE_KEY = new NamespacedKey("project_d", "updateable_timestamp");
    private static RPGItemLoreRenderer renderer = new BukkitLoreRenderer();

    public static void setRenderer(RPGItemLoreRenderer renderer) {
        BukkitItemStackAdapter.renderer = renderer;
    }

    public static ItemStack toItemStack(RPGItem rpgItem) {
        ItemStack itemStack = new ItemStack(Material.valueOf(rpgItem.getMaterial()));
        ItemMeta meta = itemStack.getItemMeta();

        if (meta == null) {
            return itemStack;
        }

        meta.setDisplayName(BukkitTextColorAdapter.colored(TextColor.GOLD, rpgItem.getName()));
        meta.setLore(renderer.render(rpgItem));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getAttributeModifiers().clear();

        // Persist item id & uuid
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ITEM_ID_KEY, PersistentDataType.STRING, rpgItem.getId());
        pdc.set(UUID_ID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());

        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public static String getRpgItemId(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(ITEM_ID_KEY, PersistentDataType.STRING);
    }

    public static UUID getUUID(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String name = stack.getItemMeta().getPersistentDataContainer().get(UUID_ID_KEY, PersistentDataType.STRING);
        return name == null ? null : UUID.fromString(name);
    }

    /**
     * Set the timestamp when this item becomes updateable again. Pass -1 to make it
     * permanently unupdateable.
     */
    public static void setUpdateableTimestamp(ItemStack stack, long timestamp) {
        if (stack == null || !stack.hasItemMeta())
            return;
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(UPDATEABLE_KEY, PersistentDataType.LONG, timestamp);
        stack.setItemMeta(meta);
    }

    /**
     * Get the timestamp when this item can be updated again. Returns -1 if
     * permanently locked.
     */
    public static long getUpdateableTimestamp(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta())
            return 0;
        return stack.getItemMeta().getPersistentDataContainer().getOrDefault(UPDATEABLE_KEY, PersistentDataType.LONG,
                0L);
    }

    /**
     * Checks if the item is currently updateable.
     */
    public static boolean isUpdateable(ItemStack stack) {
        long ts = getUpdateableTimestamp(stack);
        if (ts == -1)
            return false; // permanently locked
        return ts <= System.currentTimeMillis();
    }

}