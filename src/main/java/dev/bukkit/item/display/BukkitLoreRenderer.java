package dev.bukkit.item.display;

import static dev.bukkit.item.display.BukkitTextColorAdapter.colored;
import static dev.bukkit.item.display.BukkitTextColorAdapter.toChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.ChatColor;

import dev.core.ability.Ability;
import dev.core.ability.AbilityTriggerType;
import dev.core.item.RPGItem;
import dev.core.item.display.RPGItemLoreRenderer;
import dev.core.item.display.StyleTagParser;
import dev.core.item.display.TextColor;
import dev.core.stat.StatType;
import dev.core.stat.modifier.StatModifier;

public class BukkitLoreRenderer implements RPGItemLoreRenderer {

	@Override
	public List<String> render(RPGItem item) {
		List<String> lore = new ArrayList<>();

		// Passive stats
		if (!item.getPassiveStats().isEmpty()) {
			lore.add(colored(TextColor.GRAY, "Passive Stats:"));
			for (StatModifier stat : item.getPassiveStats()) {
				lore.add(colored(TextColor.BLUE, formatStat(stat)));
			}
			lore.add("");
		}
		// Active stats
		if (!item.getActiveStats().isEmpty()) {
			lore.add(colored(TextColor.GRAY, "Active Stats (Only applied when held):"));
			for (StatModifier stat : item.getActiveStats()) {
				lore.add(colored(TextColor.GREEN, formatStat(stat)));
			}
			lore.add("");
		}

		// --- Abilities ---
		List<Ability> abilities = item.getAbilities();
		if (!abilities.isEmpty()) {
			lore.add(ChatColor.GOLD + "Item Abilities:");

			StyleTagParser parser = new StyleTagParser(TextColor.GRAY);
			for (int i = 0; i < abilities.size(); i++) {
				Ability ability = abilities.get(i);
				if (i > 1) {
					lore.add("");

				}
				lore.add(ChatColor.YELLOW.toString() + "Ability: " + ability.getName()
						+ (ability.getTriggerType() == AbilityTriggerType.MANUAL ? " " + ChatColor.DARK_GRAY
								+ ChatColor.BOLD + ability.getAction().toString().replaceAll("_", " ") : ""));

				for (String line : ability.getDescription()) {
					StringBuilder parsedLine = new StringBuilder();

					for (StyleTagParser.StyledSegment seg : parser.parse(line)) {
						parsedLine.append(toChatFormatting(seg.style())).append(seg.text());
					}

					lore.add(parsedLine.toString());
				}

				lore.add("");

				if (ability.getCost().hasCost()) {
					lore.add(ChatColor.DARK_GRAY + "Cost: ");
					for (Entry<String, Double> entry : ability.getCost().getResourceCosts().entrySet()) {
						StatType type = StatType.valueOf(entry.getKey());
						lore.add(colored(type.getColor(), type.formatValue(entry.getValue(), true)));
					}
				}
				lore.add("");

				if (ability.getCooldown() > 0) {
					lore.add(ChatColor.DARK_GRAY + "Cooldown: " + ability.getCooldown() / 1000.0 + "s");
				}

			}
		}

		// --- Item set ---
		item.getItemSet().ifPresent(set -> {
			lore.add(colored(TextColor.DARK_PURPLE, "Part of the " + set.getName() + " Set"));
			set.getBonuses().forEach((pieces, bonus) -> {
				lore.add(colored(TextColor.GRAY, pieces + "-Piece Bonus: ")
						+ colored(TextColor.LIGHT_PURPLE, bonus.getDescription()));
			});
		});

		return lore;
	}

	private String formatStat(StatModifier stat) {
		return BukkitTextColorAdapter.toChatColor(stat.statType.getColor())
				+ stat.statType.formatValue(stat.amount, true);
	}

}
