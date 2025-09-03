package dev.bukkit.item;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.core.ability.Ability;
import dev.core.item.RPGItem;
import dev.core.item.RPGItemSet;
import dev.core.stat.StatModifier;

public class ItemStackAdapter {

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
			lore.add(""); // empty line
		}

		// --- Active stats ---
		if (!rpgItem.getActiveStats().isEmpty()) {
			lore.add(ChatColor.GRAY + "Active Stats:");
			for (StatModifier stat : rpgItem.getActiveStats()) {
				lore.add(ChatColor.GREEN + formatStat(stat));
			}
			lore.add(""); // empty line
		}

		// --- Abilities ---
		if (!rpgItem.getAbilities().isEmpty()) {
			lore.add(ChatColor.GOLD + "Item Abilities:");
			for (Ability ability : rpgItem.getAbilities()) {
				lore.add(ChatColor.YELLOW + ability.getName() + ChatColor.GRAY + " - " + ability.getDescription());
				if (ability.getCooldown() > 0) {
					lore.add(ChatColor.DARK_GRAY + "Cooldown: " + ability.getCooldown() / 1000 + "s");
				}
				lore.add(""); // spacing between abilities
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
		itemStack.setItemMeta(meta);

		return itemStack;
	}

	private static String formatStat(StatModifier stat) {
		// Example: "+50 Health" or "+10% Crit Chance"
		String sign = stat.amount >= 0 ? "+" : "-";
		return sign + stat.amount + " " + stat.statType.name();
	}

}
