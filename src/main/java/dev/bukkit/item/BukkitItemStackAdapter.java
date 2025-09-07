package dev.bukkit.item;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

}