package dev.bukkit.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.bukkit.event.BukkitEventBus;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityManager;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.item.ItemType;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.impl.ResourceStat;

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

    @AfterEach
    void clearEntityManager() {
        EntityManager.getInstance().clear();
    }

    private Player player() {
        return player(UUID.randomUUID());
    }

    private Player player(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
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
        BowArrowManager.onShoot(player, mock(Projectile.class));

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

    @Test
    void shoot_stampsRpgScaledDamageOnProjectile() {
        UUID uuid = UUID.randomUUID();
        EntityManager.getInstance().registerEntity(new TestRPGEntity(uuid, statsManager()));
        Player player = player(uuid);
        Projectile projectile = mock(Projectile.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(projectile.getPersistentDataContainer()).thenReturn(pdc);

        BowArrowManager.onShoot(player, projectile);

        ArgumentCaptor<Double> captor = ArgumentCaptor.forClass(Double.class);
        verify(pdc).set(eq(BowArrowManager.ARROW_DAMAGE_KEY), eq(PersistentDataType.DOUBLE), captor.capture());
        // 150 ATTACK_DAMAGE * 1.1 projectile multiplier (10% PROJECTILE_DAMAGE)
        assertEquals(165.0, captor.getValue(), 0.001, "arrow damage must be RPG-scaled at release");
    }

    @Test
    void shoot_unregisteredPlayerLeavesProjectileUnscaled() {
        Player player = player();
        Projectile projectile = mock(Projectile.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(projectile.getPersistentDataContainer()).thenReturn(pdc);

        BowArrowManager.onShoot(player, projectile);

        verify(pdc, org.mockito.Mockito.never()).set(any(NamespacedKey.class), any(), any());
    }

    @Test
    void isPlantedArrows_trueForArrowItemsWhileBowing() {
        Player player = player();
        slots[2] = rpgStack(BOW_ID);
        BowArrowManager.onSlotSwap(player, 2);

        assertTrue(BowArrowManager.isPlantedArrows(player, plainStack(Material.ARROW)));
        assertFalse(BowArrowManager.isPlantedArrows(player, plainStack(Material.DIAMOND)),
                "non-arrow items are never planted arrows");
    }

    @Test
    void isPlantedArrows_falseWhenNotBowing() {
        Player player = player();
        assertFalse(BowArrowManager.isPlantedArrows(player, plainStack(Material.ARROW)));

        slots[2] = rpgStack(BOW_ID);
        BowArrowManager.onSlotSwap(player, 2);
        BowArrowManager.onCleanup(player);
        assertFalse(BowArrowManager.isPlantedArrows(player, plainStack(Material.ARROW)),
                "after bow mode ends, arrows are no longer planted");
    }

    @Test
    void isPlantedArrowSlot_onlyTrueForThePlantedSlotWhileBowing() {
        Player player = player();
        slots[2] = rpgStack(BOW_ID);
        BowArrowManager.onSlotSwap(player, 2);

        assertTrue(BowArrowManager.isPlantedArrowSlot(player, 9));
        assertFalse(BowArrowManager.isPlantedArrowSlot(player, 10));
        assertFalse(BowArrowManager.isPlantedArrowSlot(player, 8), "hotbar slots are never planted");

        BowArrowManager.onCleanup(player);
        assertFalse(BowArrowManager.isPlantedArrowSlot(player, 9));
    }

    @Test
    void isPlantedArrowSlot_tracksOverriddenSlot() {
        Player player = player();
        slots[2] = rpgStack(BOW_ID);
        fillMainInventory(new ItemStack(Material.DIAMOND, 3));
        BowArrowManager.onSlotSwap(player, 2);

        assertTrue(BowArrowManager.isPlantedArrowSlot(player, 35), "highest-index slot holds the planted arrow");
        assertFalse(BowArrowManager.isPlantedArrowSlot(player, 34));
    }

    @Test
    void isArrowMaterial_recognizesAllArrowItemTypes() {
        assertTrue(BowArrowManager.isArrowMaterial(Material.ARROW));
        assertTrue(BowArrowManager.isArrowMaterial(Material.TIPPED_ARROW));
        assertTrue(BowArrowManager.isArrowMaterial(Material.SPECTRAL_ARROW));
        assertFalse(BowArrowManager.isArrowMaterial(Material.BOW));
        assertFalse(BowArrowManager.isArrowMaterial(Material.AIR));
    }

    @Test
    void shoot_disallowsPickupOnFiredArrow() {
        Player player = player();
        slots[2] = rpgStack(BOW_ID);
        BowArrowManager.onSlotSwap(player, 2);
        AbstractArrow arrow = mock(AbstractArrow.class);
        when(arrow.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));

        BowArrowManager.onShoot(player, arrow);

        verify(arrow).setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    }

    @Test
    void shoot_keepsPickupAllowedForBounceMarkedArrow() {
        Player player = player();
        slots[2] = rpgStack(BOW_ID);
        BowArrowManager.onSlotSwap(player, 2);
        AbstractArrow arrow = mock(AbstractArrow.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(arrow.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(BowArrowManager.BOUNCE_KEY, PersistentDataType.BOOLEAN)).thenReturn(true);

        BowArrowManager.onShoot(player, arrow);

        verify(arrow, org.mockito.Mockito.never()).setPickupStatus(any());
    }

    @Test
    void isBounceArrow_readsBounceKeyFromProjectileAndItemStack() {
        Projectile plain = mock(Projectile.class);
        when(plain.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        assertFalse(BowArrowManager.isBounceArrow(plain));

        AbstractArrow bounce = mock(AbstractArrow.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(bounce.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(BowArrowManager.BOUNCE_KEY, PersistentDataType.BOOLEAN)).thenReturn(true);
        assertTrue(BowArrowManager.isBounceArrow(bounce));

        assertFalse(BowArrowManager.isBounceArrow(plainStack(Material.ARROW)),
                "plain arrow items carry no bounce mark");

        ItemStack bounceStack = mock(ItemStack.class);
        when(bounceStack.hasItemMeta()).thenReturn(true);
        ItemMeta meta = mock(ItemMeta.class);
        when(bounceStack.getItemMeta()).thenReturn(meta);
        PersistentDataContainer stackPdc = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(stackPdc);
        when(stackPdc.get(BowArrowManager.BOUNCE_KEY, PersistentDataType.BOOLEAN)).thenReturn(true);
        assertTrue(BowArrowManager.isBounceArrow(bounceStack));
    }

    // ---------------------------------------------------------------- stubs

    private static StatManager statsManager() {
        Map<StatType, Stat> stats = new HashMap<>();
        long now = System.currentTimeMillis();
        stats.put(StatType.HEALTH_RESOURCE, new ResourceStat("health", t -> 100.0, t -> 0.0, now));
        stats.put(StatType.ATTACK_DAMAGE, new CombatStat("ATTACK_DAMAGE", 150));
        stats.put(StatType.PROJECTILE_DAMAGE, new CombatStat("PROJECTILE_DAMAGE", 10));
        return new StatManager(stats);
    }

    private static final class TestRPGEntity extends RPGEntity {
        TestRPGEntity(UUID uuid, StatManager statManager) {
            super(statManager, uuid, "archer", EntityType.PLAYER, new NoopEffectManager(),
                    BukkitEventBus.getInstance(), RPGClassType.ARCHER);
        }
    }

    private static final class NoopEffectManager implements EffectManagerInterface {
        @Override
        public dev.core.ability.Effect cast(RPGEntity entity, dev.core.ability.Ability ability) {
            return null;
        }

        @Override
        public boolean canActivate(RPGEntity entity, dev.core.ability.Ability ability) {
            return false;
        }

        @Override
        public long remainingCooldown(RPGEntity entity, dev.core.ability.Ability ability) {
            return 0;
        }

        @Override
        public void tick(long now) {
        }

        @Override
        public void cancelAll() {
        }
    }
}