package dev.bukkit.item;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import dev.core.ability.Ability;
import dev.core.item.RPGItem;
import dev.core.item.RPGItemSet;
import dev.core.stat.StatModifier;

public class ItemStackAdapter {

	private static final NamespacedKey ITEM_ID_KEY = new NamespacedKey("project_d", "rpgitem_id");
	public static final NamespacedKey UUID_ID_KEY = new NamespacedKey("project_d", "uuid");

	public static ItemStack toItemStack(RPGItem rpgItem, Material material) {
		ItemStack itemStack = new ItemStack(material);
		ItemMeta meta = itemStack.getItemMeta();

		if (meta == null) {
			return itemStack;
		}

		meta.setDisplayName(ChatColor.GOLD + rpgItem.getName());

		List<String> lore = new ArrayList<>();

		// --- Passive stats ---
		if (!rpgItem.getPassiveStats().isEmpty()) {
			lore.add(ChatColor.GRAY + "Passive Stats:");
			for (StatModifier stat : rpgItem.getPassiveStats()) {
				lore.add(ChatColor.BLUE + formatStat(stat));
			}
			lore.add("");
		}

		// --- Active stats ---
		if (!rpgItem.getActiveStats().isEmpty()) {
			lore.add(ChatColor.GRAY + "Active Stats:");
			for (StatModifier stat : rpgItem.getActiveStats()) {
				lore.add(ChatColor.GREEN + formatStat(stat));
			}
			lore.add("");
		}

		// --- Abilities ---
		if (!rpgItem.getAbilities().isEmpty()) {
			lore.add(ChatColor.GOLD + "Item Abilities:");
			for (Ability ability : rpgItem.getAbilities()) {
				lore.add(ChatColor.YELLOW + ability.getName() + ChatColor.GRAY + " - " + ability.getDescription());
				if (ability.getCooldown() > 0) {
					lore.add(ChatColor.DARK_GRAY + "Cooldown: " + ability.getCooldown() / 1000.0 + "s");
				}
				lore.add("");
			}
		}

		// --- Set Bonus ---
		if (rpgItem.getItemSet().isPresent()) {
			RPGItemSet set = rpgItem.getItemSet().get();
			lore.add(ChatColor.DARK_PURPLE + "Part of the " + set.getName() + " Set");
			set.getBonuses().forEach((pieces, bonus) -> {
				lore.add(ChatColor.GRAY + "" + pieces + "-Piece Bonus: " + ChatColor.LIGHT_PURPLE
						+ bonus.getDescription());
			});
		}

		meta.setLore(lore);

		// --- Persist RPG Item ID ---
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

	private static String formatStat(StatModifier stat) {
		String sign = stat.amount >= 0 ? "+" : "";
		return sign + stat.amount + " " + stat.statType.name();
	}
}
