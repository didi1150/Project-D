package dev.bukkit.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.core.item.ItemType;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.item.loader.RPGItemRegistry;

class BowArrowManagerTest {

    private static final String BOW_ID = "TEST_BOW";
    private static final String ARROWLESS_BOW_ID = "TEST_ARROWLESS_BOW";

    private final ItemStack[] slots = new ItemStack[36];
    private final PlayerInventory inventory = mock(PlayerInventory.class);

    @BeforeAll
    static void registerTestItems() {
        RPGItem bow = RPGItem.builder(BOW_ID, "Test Bow", "BOW", EquipmentSlot.MAIN_HAND)
                .withItemType(ItemType.BOW)
                .requiresArrows(true)
                .build();
        RPGItem arrowless = RPGItem.builder(ARROWLESS_BOW_ID, "Test Arrowless Bow", "BONE", EquipmentSlot.MAIN_HAND)
                .withItemType(ItemType.BOW)
                .requiresArrows(false)
                .build();
        RPGItemRegistry.getInstance().addAll(Map.of(BOW_ID, bow, ARROWLESS_BOW_ID, arrowless));
    }

    @AfterAll
    static void unregisterTestItems() {
        RPGItem stub = RPGItem.builder("GONE", "Gone", EquipmentSlot.MAIN_HAND).build();
        RPGItemRegistry.getInstance().addAll(Map.of(BOW_ID, stub, ARROWLESS_BOW_ID, stub));
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItem(anyInt())).thenAnswer(invocation -> slots[(int) invocation.getArgument(0)]);
        doAnswer(invocation -> {
            slots[(int) invocation.getArgument(0)] = invocation.getArgument(1);
            return null;
        }).when(inventory).setItem(anyInt(), any());
        when(inventory.getItemInMainHand()).thenAnswer(invocation -> slots[0]);
        return player;
    }

    /** A stack whose PDC advertises the given RPG item id. */
    private ItemStack rpgStack(String id) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.BOW);
        when(stack.hasItemMeta()).thenReturn(true);
        ItemMeta meta = mock(ItemMeta.class);
        when(stack.getItemMeta()).thenReturn(meta);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(NamespacedKey.class), any(PersistentDataType.class))).thenReturn(id);
        return stack;
    }

    /**
     * A plain non-RPG stack. Must be mocked: real ItemStack meta methods hit
     * Bukkit.getItemFactory(), which needs a running server.
     */
    private ItemStack plainStack(Material material) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(material);
        when(stack.hasItemMeta()).thenReturn(false);
        return stack;
    }

    private void fillMainInventory(ItemStack filler) {
        for (int slot = 9; slot <= 35; slot++) {
            slots[slot] = filler;
        }
    }

    @Test
    void swapToBow_placesArrowInFirstFreeMainInventorySlot() {
        Player player = player();
        slots[2] = rpgStack(BOW_ID);

        BowArrowManager.onSlotSwap(player, 2);

        assertTrue(BowArrowManager.isBowing(player));
        assertNotNull(slots[9], "arrow expected in lowest free main slot");
        assertEquals(Material.ARROW, slots[9].getType());
        assertEquals(64, slots[9].getAmount());
    }

    @Test
    void swapAway_clearsFreeSlotArrow() {
        Player player = player();
        slots[2] = rpgStack(BOW_ID);
        BowArrowManager.onSlotSwap(player, 2);
        assertTrue(BowArrowManager.isBowing(player));

        slots[2] = rpgStack("SOME_OTHER");
        BowArrowManager.onSlotSwap(player, 4);

        assertFalse(BowArrowManager.isBowing(player));
        assertNull(slots[9], "free-slot arrow must be removed on swap away");
    }

    @Test
    void swapToBow_withFullMainInventory_overridesHighestOccupiedSlot() {
        Player player = player();
        slots[2] = rpgStack(BOW_ID);
        fillMainInventory(new ItemStack(Material.DIAMOND, 3));

        BowArrowManager.onSlotSwap(player, 2);

        assertTrue(BowArrowManager.isBowing(player));
        assertEquals(Material.ARROW, slots[35].getType(), "highest-index main slot overridden");
        assertEquals(Material.DIAMOND, slots[34].getType(), "lower slots stay untouched");
    }

    @Test
    void swapAway_restoresOverriddenItem() {
        Player player = player();
        ItemStack sacrifice = new ItemStack(Material.DIAMOND, 3);
        slots[2] = rpgStack(BOW_ID);
        fillMainInventory(sacrifice);
        BowArrowManager.onSlotSwap(player, 2);
        assertTrue(BowArrowManager.isBowing(player));

        slots[2] = rpgStack("SOME_OTHER");
        BowArrowManager.onSlotSwap(player, 2);
        assertFalse(BowArrowManager.isBowing(player));
        assertEquals(sacrifice, slots[35], "stored item must be restored to its slot");
    }

    @Test
    void shoot_refreshesArrowButKeepsStoredItem() {
        Player player = player();
        ItemStack sacrifice = new ItemStack(Material.DIAMOND, 3);
        slots[2] = rpgStack(BOW_ID);
        fillMainInventory(sacrifice);
        BowArrowManager.onSlotSwap(player, 2);

        // player fires: arrow slot drops to a nearly-empty stack as if consumed
        slots[35] = new ItemStack(Material.ARROW, 1);
        BowArrowManager.onShoot(player);

        assertEquals(64, slots[35].getAmount(), "arrow stack refreshed after shot");

        slots[2] = rpgStack("SOME_OTHER");
        BowArrowManager.onSlotSwap(player, 2);
        assertEquals(sacrifice, slots[35], "stored item untouched by shooting");
    }

    @Test
    void bowWithoutArrows_neverArms() {
        Player player = player();
        slots[2] = rpgStack(ARROWLESS_BOW_ID);

        BowArrowManager.onSlotSwap(player, 2);

        assertFalse(BowArrowManager.isBowing(player));
        assertNull(slots[9]);
    }

    @Test
    void cleanup_restoresStoredItemAndClearsState() {
        Player player = player();
        ItemStack sacrifice = new ItemStack(Material.DIAMOND, 3);
        slots[2] = rpgStack(BOW_ID);
        fillMainInventory(sacrifice);
        BowArrowManager.onSlotSwap(player, 2);

        BowArrowManager.onCleanup(player);

        assertFalse(BowArrowManager.isBowing(player));
        assertEquals(sacrifice, slots[35]);
    }

    @Test
    void swapHands_outOfBowRestores() {
        Player player = player();
        slots[2] = rpgStack(BOW_ID);
        BowArrowManager.onSlotSwap(player, 2);
        assertTrue(BowArrowManager.isBowing(player));

        // bow moved to the offhand: main hand is now a stick
        slots[0] = plainStack(Material.STICK);
        BowArrowManager.onSwapHands(player);

        assertFalse(BowArrowManager.isBowing(player));
        assertNull(slots[9]);
    }

    @Test
    void swapHands_backToBowRearms() {
        Player player = player();
        slots[0] = rpgStack(BOW_ID);
        slots[2] = plainStack(Material.STICK);

        BowArrowManager.onSwapHands(player);

        assertTrue(BowArrowManager.isBowing(player));
        assertEquals(Material.ARROW, slots[9].getType());
    }
}