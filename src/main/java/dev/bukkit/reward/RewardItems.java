package dev.bukkit.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.core.reward.RewardDefinition;
import dev.core.utils.AdventureText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public final class RewardItems {

    private static final Material[] BLUE_DYES = { Material.BLUE_DYE, Material.LIGHT_BLUE_DYE,
            Material.CYAN_DYE };

    private RewardItems() {
    }

    public static void giveOrDrop(Player player, ItemStack stack) {
        if (stack == null) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack remaining : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), remaining);
        }
    }

    /**
     * A dye icon for XP rewards. The actual material and text tint are both
     * randomized inside a blue palette.
     */
    public static ItemStack createDyeIcon(RewardDefinition reward, Random random) {
        Material material = BLUE_DYES[random.nextInt(BLUE_DYES.length)];
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        TextColor shade = TextColor.color(20 + random.nextInt(70), 100 + random.nextInt(90),
                200 + random.nextInt(56));
        Component name = Component.text(reward.displayName()).color(shade);
        meta.displayName(AdventureText.withoutItalic(name));

        List<String> lore = new ArrayList<>(reward.description());
        if (!lore.isEmpty()) {
            lore.add("");
        }
        lore.add("§7Grants §b" + reward.xpMin() + "-" + reward.xpMax()
                + " Class XP§7 to your active class.");
        meta.lore(AdventureText.itemLoreLines(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }
}
