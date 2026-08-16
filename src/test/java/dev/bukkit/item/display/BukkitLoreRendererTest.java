package dev.bukkit.item.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import dev.core.item.ItemType;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;

class BukkitLoreRendererTest {

    private final BukkitLoreRenderer renderer = new BukkitLoreRenderer();

    @Test
    void lore_endsWithBoldGoldLevelAndTypeLine() {
        RPGItem sword = RPGItem.builder("TEST_SWORD", "Test Sword", EquipmentSlot.MAIN_HAND)
                .withMaterial("IRON_SWORD")
                .withUnlockLevel(5)
                .withItemType(ItemType.SWORD)
                .build();

        List<String> lore = renderer.render(sword);

        String expected = ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + "LEVEL 5: SWORD ITEM";
        assertEquals(expected, lore.get(lore.size() - 1));
    }

    @Test
    void lore_showsBowTypeAtLevelZero() {
        RPGItem bow = RPGItem.builder("TEST_BOW", "Test Bow", EquipmentSlot.MAIN_HAND)
                .withMaterial("BOW")
                .withItemType(ItemType.BOW)
                .build();

        List<String> lore = renderer.render(bow);

        String expected = ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + "LEVEL 0: BOW ITEM";
        assertEquals(expected, lore.get(lore.size() - 1));
        assertTrue(lore.get(lore.size() - 1).startsWith(ChatColor.GOLD.toString()),
                "level line must be gold");
        assertTrue(lore.get(lore.size() - 1).startsWith(ChatColor.GOLD.toString() + ChatColor.BOLD.toString()),
                "level line must be bold");
    }

    @Test
    void lore_holderRenderHasSameLevelLine() {
        RPGItem helmet = RPGItem.builder("TEST_HELMET", "Test Helmet", "LEATHER_HELMET", EquipmentSlot.HEAD)
                .withUnlockLevel(3)
                .withItemType(ItemType.HELMET)
                .build();

        List<String> lore = renderer.render(helmet, null);

        String expected = ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + "LEVEL 3: HELMET ITEM";
        assertEquals(expected, lore.get(lore.size() - 1));
    }
}