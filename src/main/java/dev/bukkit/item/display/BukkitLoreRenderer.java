package dev.bukkit.item.display;

import static dev.bukkit.item.display.BukkitTextColorAdapter.colored;
import static dev.bukkit.item.display.BukkitTextColorAdapter.toChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.summon.SoulFragment;
import dev.bukkit.summon.SoulTome;
import dev.bukkit.summon.SummonStats;
import dev.bukkit.utils.ManaDiscountUtils;
import dev.core.ability.Ability;
import dev.core.ability.AbilityTriggerType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.item.RPGItem;
import dev.core.item.display.RPGItemLoreRenderer;
import dev.core.item.display.StyleTagParser;
import dev.core.item.display.TextColor;
import dev.core.stat.StatType;
import dev.core.stat.modifier.StatModifier;

public class BukkitLoreRenderer implements RPGItemLoreRenderer {

    @Override
    public List<String> render(RPGItem item) {
        return render(item, null);
    }

    @Override
    public List<String> render(RPGItem item, RPGEntity holder) {
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
                // Defensive: an ability attached to an item but not configured in
                // abilities.yml keeps null name/description/action.
                String abilityName = ability.getName() != null ? ability.getName() : ability.getId();
                if (ability.getTriggerType() == AbilityTriggerType.PASSIVE) {
                    lore.add(ChatColor.YELLOW.toString() + "Passive: " + abilityName);
                } else {
                    lore.add(ChatColor.YELLOW.toString() + "Ability: " + abilityName
                            + (ability.getTriggerType() == AbilityTriggerType.MANUAL && ability.getAction() != null
                                    ? " " + ChatColor.DARK_GRAY + ChatColor.BOLD
                                            + ability.getAction().toString().replaceAll("_", " ")
                                    : ""));
                }

                List<String> description = ability.getDescription();
                if (description != null) {
                    for (String line : description) {
                        if (line == null) {
                            continue;
                        }
                        StringBuilder parsedLine = new StringBuilder();

                        for (StyleTagParser.StyledSegment seg : parser.parse(line)) {
                            parsedLine.append(toChatFormatting(seg.style())).append(seg.text());
                        }

                        lore.add(parsedLine.toString());
                    }
                }

                boolean line = false;

                if (ability.getCost() != null && ability.getCost().hasCost()) {
                    line = true;
                    lore.add("");
                    lore.add(ChatColor.DARK_GRAY + "Cost: ");
                    boolean discounted = false;
                    for (Entry<String, Double> entry : ability.getCost().getResourceCosts().entrySet()) {
                        StatType type = StatType.valueOf(entry.getKey());
                        double cost = ManaDiscountUtils.discountedCost(holder, entry.getKey(), entry.getValue());
                        if (cost < entry.getValue()) {
                            discounted = true;
                        }
                        lore.add(colored(type.getColor(), type.formatValue(cost, true)));
                    }
                    if (discounted) {
                        lore.add(colored(TextColor.DARK_PURPLE, "Mana costs reduced by 10% (Mage Set)"));
                    }
                }

                if (ability.getCooldown() > 0) {
                    line = true;
                    lore.add("");
                    lore.add(ChatColor.DARK_GRAY + "Cooldown: " + ability.getCooldown() / 1000.0 + "s");
                }
                if (!line) {
                    lore.add("");
                }
            }
        }

        // --- Soul Tome: souls currently held on the stack ---
        if (SoulTome.ITEM_ID.equals(item.getId())) {
            renderTomeSouls(lore, holder);
        }

        // --- Item set ---
        item.getItemSet().ifPresent(set -> {
            lore.add(colored(TextColor.DARK_PURPLE, "Part of the " + set.getName() + " Set"));
            set.getBonuses().forEach((pieces, bonus) -> {
                lore.add(colored(TextColor.GRAY, pieces + "-Piece Bonus: ")
                        + colored(TextColor.LIGHT_PURPLE, bonus.getDescription()));
            });
        });

        // --- Level / type ---
        lore.add(ChatColor.GOLD.toString() + ChatColor.BOLD + "LEVEL " + item.getUnlockLevel() + ": "
                + item.getItemType().name() + " ITEM");

        return lore;
    }

    private String formatStat(StatModifier stat) {
        return BukkitTextColorAdapter.toChatColor(stat.statType.getColor())
                + stat.statType.formatValue(stat.amount, true);
    }

    /**
     * Appends the tome-specific soul lines: the filled/total capacity and one line
     * per soul currently held on the player's tome stack. Dynamic per holder, so a
     * holder-less render (e.g. shop preview) shows only the capacity hint.
     */
    private void renderTomeSouls(List<String> lore, RPGEntity holder) {
        Player player = holder instanceof BukkitPlayerEntity playerEntity ? playerEntity.getPlayer().orElse(null)
                : null;
        Integer capacity = null;
        int held = 0;
        List<SoulFragment> souls = List.of();
        if (player != null && player.isOnline()) {
            ItemStack tome = SoulTome.findTome(player);
            if (tome != null) {
                souls = SoulTome.getSouls(tome);
                held = souls.size();
                int level = DMain.getInstance().getProgressionService()
                        .getProgression(player.getUniqueId(), RPGClassType.SUPPORT).getLevel();
                capacity = SummonStats.capacityForLevel(level);
            }
        }
        lore.add("");
        if (capacity == null) {
            lore.add(ChatColor.DARK_PURPLE + "Souls held: see your own tome.");
        } else {
            lore.add(ChatColor.DARK_PURPLE + "Souls: " + held + "/" + capacity);
            if (souls.isEmpty()) {
                lore.add(ChatColor.GRAY + "No souls captured yet. Walk into");
                lore.add(ChatColor.GRAY + "dropped souls to claim them.");
            } else {
                for (SoulFragment soul : souls) {
                    String mobName = soul.mobType().name().toLowerCase().replace('_', ' ');
                    mobName = mobName.isEmpty() ? mobName
                            : Character.toUpperCase(mobName.charAt(0)) + mobName.substring(1);
                    lore.add(ChatColor.GRAY + "- " + mobName + " (" + soul.tier().name().toLowerCase() + ")");
                }
            }
        }
    }

}
