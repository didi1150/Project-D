package dev.bukkit.item.display;

import static dev.bukkit.item.display.BukkitTextColorAdapter.colored;
import static dev.bukkit.item.display.BukkitTextColorAdapter.toChatFormatting;

import java.util.ArrayList;
import java.util.List;

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
import dev.core.ability.CooldownScaling;
import dev.core.ability.CooldownScope;
import dev.core.ability.CostEntry;
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
            lore.add(LoreLabels.format(LoreLabels.PASSIVE_STATS_HEADER));
            for (StatModifier stat : item.getPassiveStats()) {
                lore.add(colored(TextColor.BLUE, formatStat(stat)));
            }
            lore.add("");
        }
        // Active stats
        if (!item.getActiveStats().isEmpty()) {
            lore.add(LoreLabels.format(LoreLabels.ACTIVE_STATS_HEADER));
            for (StatModifier stat : item.getActiveStats()) {
                lore.add(colored(TextColor.GREEN, formatStat(stat)));
            }
            lore.add("");
        }

        // --- Item description ---
        StyleTagParser descriptionParser = new StyleTagParser(TextColor.GRAY);
        List<String> itemDescription = item.getDescription();
        if (!itemDescription.isEmpty()) {
            for (String line : itemDescription) {
                if (line == null) {
                    continue;
                }
                StringBuilder parsedLine = new StringBuilder();
                for (StyleTagParser.StyledSegment seg : descriptionParser.parse(line)) {
                    parsedLine.append(toChatFormatting(seg.style())).append(seg.text());
                }
                lore.add(parsedLine.toString());
            }
            lore.add("");
        }

        // --- Abilities ---
        List<Ability> abilities = item.getAbilities();
        if (!abilities.isEmpty()) {
            lore.add(LoreLabels.format(LoreLabels.ABILITIES_HEADER));

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
                    lore.add(LoreLabels.format(LoreLabels.PASSIVE_LABEL, "name", abilityName));
                } else {
                    String actionSuffix = ability.getTriggerType() == AbilityTriggerType.MANUAL
                            && ability.getAction() != null
                                    ? LoreLabels.format(LoreLabels.ACTION_SUFFIX, "action",
                                            ability.getAction().toString().replaceAll("_", " "))
                                    : "";
                    lore.add(LoreLabels.format(LoreLabels.ABILITY_LABEL, "name", abilityName) + actionSuffix);
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
                    lore.add(LoreLabels.format(LoreLabels.COST_LABEL));
                    boolean discounted = false;
                    for (CostEntry cost : ability.getCost().getCosts()) {
                        StatType type = StatType.valueOf(cost.mode().getResourceType());
                        // Dynamic (formula) costs resolve against the viewing
                        // holder so the lore shows the price they would pay.
                        double base = cost.resolve(holder);
                        double amount = ManaDiscountUtils.discountedCost(holder, cost.mode().getResourceType(), base);
                        if (amount < base) {
                            discounted = true;
                        }
                        lore.add(BukkitTextColorAdapter.formatStat(type, amount, true));
                    }
                    if (discounted) {
                        lore.add(LoreLabels.format(LoreLabels.MANA_DISCOUNT_NOTE));
                    }
                }

                if (ability.getCooldown() > 0) {
                    line = true;
                    lore.add("");
                    String scalingSuffix = ability.getCooldownScaling() == CooldownScaling.NONE
                            ? LoreLabels.format(LoreLabels.COOLDOWN_SCALING_NONE_SUFFIX)
                            : LoreLabels.format(LoreLabels.COOLDOWN_SCALING_HASTE_SUFFIX);
                    lore.add(LoreLabels.format(LoreLabels.COOLDOWN_LABEL, "cooldown",
                            ability.getCooldown() / 1000.0 + "s") + scalingSuffix);
                    lore.add(LoreLabels.format(ability.getScope() == CooldownScope.ITEM
                            ? LoreLabels.COOLDOWN_SCOPE_ITEM_NOTE
                            : LoreLabels.COOLDOWN_SCOPE_PLAYER_NOTE));
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
            lore.add(LoreLabels.format(LoreLabels.ITEM_SET_LINE, "set", set.getName()));
            StyleTagParser setParser = new StyleTagParser(TextColor.GRAY);
            set.getBonuses().forEach((pieces, bonus) -> {
                lore.add(LoreLabels.format(LoreLabels.SET_BONUS_LABEL, "pieces", pieces));
                if (bonus.getDescription() != null) {
                    for (String line : bonus.getDescription().split("\n")) {
                        StringBuilder parsedLine = new StringBuilder();
                        for (StyleTagParser.StyledSegment seg : setParser.parse(line)) {
                            parsedLine.append(toChatFormatting(seg.style())).append(seg.text());
                        }
                        lore.add(parsedLine.toString());
                    }
                }
            });
        });

        // --- Level / type ---
        lore.add(LoreLabels.format(LoreLabels.LEVEL_LINE,
                "level", item.getUnlockLevel(), "type", item.getItemType().name()));

        return lore;
    }

    private String formatStat(StatModifier stat) {
        return BukkitTextColorAdapter.formatStat(stat.statType, stat.amount, true);
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
