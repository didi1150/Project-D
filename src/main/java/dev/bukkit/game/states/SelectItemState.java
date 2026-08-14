package dev.bukkit.game.states;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.GameState;
import dev.core.item.RPGItem;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.progression.PlayerClassProgression;
import dev.core.progression.PlayerProgression;

public class SelectItemState extends GameState {

    public static String NAME = "SELECTITEM";
    public static long DURATION = 20 * 60L;
    private static long lockThreshold = 10;
    private final static int minPlayers = 5;
    private static final String SHOP_TITLE = "Select an Item";
    private static final int DEFAULT_ITEM_LIMIT = 5;
    private static final int SHOP_COLUMNS = 9;
    private static final Material[] LEVEL_LABEL_COLORS = {
            Material.GRAY_STAINED_GLASS_PANE,
            Material.GREEN_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
    };

    /**
     * Per-player running selection: item id -> how many of that template have been
     * taken.
     */
    private final Map<UUID, Map<String, Integer>> selections = new HashMap<>();

    public SelectItemState(EventBusInterface eventBus) {
        super(NAME, DURATION, eventBus);
    }

    @Override
    protected void onStart() {
        // TickEvent is now sent by the base GameState for every state.
        // Open the class-filtered item shop for everyone, once the state settles.
        scheduler.runTaskLater(() -> Bukkit.getOnlinePlayers().forEach(this::openShop), 5L);

        // Tell players they can reopen the shop with /d open during this state.
        Bukkit.broadcastMessage("§eThe item draft is open! Use §6/d open§e to open the shop.");
    }

    /**
     * Opens the item draft chest for a player. Templates are the items usable by
     * the player's class (never {@code mob-only}, filtered by
     * {@code allowed-classes} — the class lock is used only here, to list a class's
     * items). Items are grouped by unlock level, each level on its own inventory
     * row headed by a level label (levels without items are skipped), so locked
     * items appear in their own row and show what is still to come. Each template's
     * stack amount shows how many of that item the player has already taken, and
     * taken templates glow in the menu (the inventory copy does not).
     */
    public void openShop(Player player) {
        Inventory shop = Bukkit.createInventory(null, 54, SHOP_TITLE);
        RPGClassType playerClass = activeClassOf(player);
        int playerLevel = levelOf(player);
        Map<String, Integer> owned = selections.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        Map<Integer, List<RPGItem>> byLevel = new TreeMap<>();
        for (RPGItem item : RPGItemRegistry.getInstance().allItems().values()) {
            if (!item.isAllowedForClass(playerClass)) {
                continue;
            }
            byLevel.computeIfAbsent(Math.max(0, item.getUnlockLevel()), k -> new ArrayList<>()).add(item);
        }

        int slot = 0;
        int levelIndex = 0;
        for (Map.Entry<Integer, List<RPGItem>> entry : byLevel.entrySet()) {
            List<RPGItem> items = entry.getValue();
            items.sort(Comparator.comparing(RPGItem::getId));
            if (slot % SHOP_COLUMNS != 0) {
                slot = slot / SHOP_COLUMNS * SHOP_COLUMNS + SHOP_COLUMNS; // each level gets its own row
            }
            shop.setItem(slot++, unlockLevelLabel(entry.getKey(), levelIndex));
            for (RPGItem item : items) {
                if (slot >= 54) {
                    break;
                }
                if (!item.isUsableAtLevel(playerLevel)) {
                    shop.setItem(slot++, BukkitItemStackAdapter.toLockedItemStack(item));
                    continue;
                }
                int count = owned.getOrDefault(item.getId(), 0);
                ItemStack stack = BukkitItemStackAdapter.toItemStack(item, rpgEntityOf(player));
                stack.setAmount(Math.max(1, count)); // a template is always visible; 0 bought shows as 1
                if (count > 0) {
                    applyGlow(stack);
                }
                shop.setItem(slot++, stack);
            }
            levelIndex++;
        }
        // Fill leftover slots for a uniform look; fillers carry no item id, so
        // clicks on them are inert.
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        while (slot < 54) {
            shop.setItem(slot++, filler.clone());
        }
        player.openInventory(shop);
        player.sendMessage("§eYou can select up to §6" + itemLimitOf(player) + "§e items. §7Armor pieces are free — one per slot.");
    }

    /** Row header for an unlock-level group: colored by position, names the level. */
    private static ItemStack unlockLevelLabel(int level, int levelIndex) {
        Material material = LEVEL_LABEL_COLORS[levelIndex % LEVEL_LABEL_COLORS.length];
        ItemStack label = new ItemStack(material);
        ItemMeta meta = label.getItemMeta();
        meta.setDisplayName(level <= 0 ? "§7No level required" : "§bRequires Level " + level);
        label.setItemMeta(meta);
        return label;
    }

    private RPGClassType activeClassOf(Player player) {
        Optional<RPGEntity> entity = EntityManager.getInstance().getEntity(player.getUniqueId());
        if (entity.isPresent() && entity.get() instanceof BukkitPlayerEntity playerEntity) {
            return playerEntity.getPlayerProgression().getActiveClass();
        }
        return RPGClassType.NONE;
    }

    /** The player's RPG entity, or {@code null} when not registered (no discount applies then). */
    private RPGEntity rpgEntityOf(Player player) {
        return EntityManager.getInstance().getEntity(player.getUniqueId()).orElse(null);
    }

    /** The active class progression of a player, or {@code null} if none exists. */
    private PlayerClassProgression activeClassProgressionOf(Player player) {
        Optional<RPGEntity> entity = EntityManager.getInstance().getEntity(player.getUniqueId());
        if (entity.isPresent() && entity.get() instanceof BukkitPlayerEntity playerEntity) {
            PlayerProgression progression = playerEntity.getPlayerProgression();
            return progression.getProgression(progression.getActiveClass());
        }
        return null;
    }

    /**
     * The player's active class level, used to gate item unlocks in the shop.
     * Falls back to 0 when no progression is available.
     */
    private int levelOf(Player player) {
        PlayerClassProgression progression = activeClassProgressionOf(player);
        return progression == null ? 0 : Math.max(0, progression.getLevel());
    }

    /**
     * The maximum number of items the player may obtain, taken from the active
     * class's {@link PlayerClassProgression#getUsableItems()} (persisted item
     * count). Falls back to the default of 5 when no progression is available.
     */
    private int itemLimitOf(Player player) {
        PlayerClassProgression progression = activeClassProgressionOf(player);
        if (progression != null) {
            return Math.max(0, progression.getUsableItems());
        }
        return DEFAULT_ITEM_LIMIT;
    }

    /**
     * Total number of non-armor items the player has already taken in this draft.
     * Armor is free (one piece per slot) and never counts against the limit.
     */
    private int selectedCountOf(Player player) {
        Map<String, Integer> owned = selections.get(player.getUniqueId());
        if (owned == null || owned.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Map.Entry<String, Integer> entry : owned.entrySet()) {
            Optional<RPGItem> itemOpt = RPGItemRegistry.getInstance().getItem(entry.getKey());
            if (itemOpt.isPresent() && !itemOpt.get().isArmor()) {
                total += entry.getValue();
            }
        }
        return total;
    }

    /** Whether the player already has an armor piece for the item's slot. */
    private boolean isArmorSlotTaken(Player player, RPGItem item) {
        Map<String, Integer> owned = selections.get(player.getUniqueId());
        if (owned == null || owned.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : owned.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            Optional<RPGItem> itemOpt = RPGItemRegistry.getInstance().getItem(entry.getKey());
            if (itemOpt.isPresent() && itemOpt.get().getEquipmentSlot() == item.getEquipmentSlot()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onStop() {
        updateCountdownXP(remainingTicks / 20, DURATION / 20);
    }

    @Override
    protected void onTickSecond(long secondsRemaining) {
        updateCountdownXP(secondsRemaining, DURATION / 20);
    }

    @Override
    protected void registerSubscribers() {
        EventAction<PlayerMoveEvent> moveAction = new EventAction<PlayerMoveEvent>(this::handleMovement,
                PlayerMoveEvent.class);
        EventAction<PlayerQuitEvent> quitAction = new EventAction<PlayerQuitEvent>(this::handleQuit,
                PlayerQuitEvent.class);
        EventAction<InventoryClickEvent> clickAction = new EventAction<InventoryClickEvent>(this::handleClick,
                InventoryClickEvent.class);
        EventAction<PlayerDropItemEvent> dropAction = new EventAction<PlayerDropItemEvent>(this::handleDrop,
                PlayerDropItemEvent.class);
        EventAction<InventoryCloseEvent> closeAction = new EventAction<InventoryCloseEvent>(this::handleClose,
                InventoryCloseEvent.class);
        EventAction<EntityDamageEvent> damageAction = new EventAction<EntityDamageEvent>(this::handleDamage,
                EntityDamageEvent.class);
        EventAction<BlockBreakEvent> blockBreakAction = new EventAction<BlockBreakEvent>(this::handleBlockBreak,
                BlockBreakEvent.class);
        EventAction<BlockPlaceEvent> blockPlaceAction = new EventAction<BlockPlaceEvent>(this::handleBlockPlace,
                BlockPlaceEvent.class);
        addSubscriber(clickAction);
        addSubscriber(closeAction);
        addSubscriber(quitAction);
        addSubscriber(moveAction);
        addSubscriber(damageAction);
        addSubscriber(blockPlaceAction);
        addSubscriber(blockBreakAction);
        addSubscriber(dropAction);
    }

    private void handleMovement(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setCancelled(true);
            return;
        }
    }

    private void handleQuit(PlayerQuitEvent event) {
        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
            jumpToState(PreLobbyState.NAME);
        }
    }

    private void handleClose(InventoryCloseEvent event) {
        if (remainingTicks / 20 <= lockThreshold) {
            return;
        }
    }

    private void handleClick(InventoryClickEvent event) {
        if (remainingTicks / 20 <= lockThreshold) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!event.getView().getTitle().equals(SHOP_TITLE)) {
            return; // not the item shop
        }
        if (event.getClickedInventory() != event.getInventory()) {
            handleReturnClick(event, player); // returning a selected item from the player's inventory
            return;
        }
        event.setCancelled(true); // never let the menu item move to the cursor
        ItemStack stack = event.getCurrentItem();
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        String itemId = BukkitItemStackAdapter.getRpgItemId(stack);
        if (itemId == null) {
            return;
        }
        RPGItemRegistry.getInstance().getItem(itemId).ifPresent(item -> {
            if (!item.isAllowedForClass(activeClassOf(player))) {
                return; // mob-only / class-gated items can't be taken
            }

            // Level gate: locked items are barrier stacks that can't be taken.
            if (!item.isUsableAtLevel(levelOf(player))) {
                player.sendMessage("§cThis item requires level " + item.getUnlockLevel() + ".");
                return;
            }

            // Armor is free but limited to one piece per slot.
            if (item.isArmor()) {
                if (isArmorSlotTaken(player, item)) {
                    player.sendMessage("§cYou can only take one piece for that armor slot.");
                    return;
                }
            } else {
                // Bound by the player's progression item count.
                int limit = itemLimitOf(player);
                if (selectedCountOf(player) >= limit) {
                    player.sendMessage("§cYou have reached your item limit (" + limit + ").");
                    return;
                }
            }
            // Bound by the player's remaining inventory slots.
            if (countEmptySlots(player) <= 0) {
                player.sendMessage("§cNo inventory space left to select more items.");
                return;
            }
            // Take one: put a plain copy into the inventory, track it for the menu.
            player.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item, rpgEntityOf(player)));
            Map<String, Integer> owned = selections.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
            int count = owned.merge(item.getId(), 1, Integer::sum);

            // Reflect the new count in the menu and glow the template (menu only).
            ItemStack updated = BukkitItemStackAdapter.toItemStack(item, rpgEntityOf(player));
            updated.setAmount(Math.max(1, count));
            applyGlow(updated);
            event.getInventory().setItem(event.getSlot(), updated);
        });
    }

    /** Empty slots in the player's storage + hotbar (excludes armor/offhand). */
    private static int countEmptySlots(Player player) {
        int empty = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                empty++;
            }
        }
        return empty;
    }

    /** Adds a cosmetic glow (no enchant lore) to a menu ItemStack. */
    private static void applyGlow(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.addEnchant(Enchantment.LURE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
    }

    /**
     * Handles a click on an item in the player's own inventory (the bottom pane of
     * the open shop): if it's a shop-bought item, one copy is removed from the
     * inventory and the menu template is updated (count decremented, glow cleared
     * once it reaches zero).
     */
    private void handleReturnClick(InventoryClickEvent event, Player player) {
        ItemStack stack = event.getCurrentItem();
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        String itemId = BukkitItemStackAdapter.getRpgItemId(stack);
        if (itemId == null) {
            return;
        }
        Map<String, Integer> owned = selections.get(player.getUniqueId());
        if (owned == null || owned.getOrDefault(itemId, 0) <= 0) {
            return; // not a shop-bought item; allow normal inventory interaction
        }
        // Cancel so we control the removal precisely (no accidental drag/swap).
        event.setCancelled(true);
        // Remove one copy from the clicked slot.
        int newAmount = stack.getAmount() - 1;
        if (newAmount <= 0) {
            event.getClickedInventory().setItem(event.getSlot(), null);
        } else {
            stack.setAmount(newAmount);
            event.getClickedInventory().setItem(event.getSlot(), stack);
        }
        // Decrement the tracked selection; drop the entry once it reaches zero.
        int remaining = owned.merge(itemId, -1, Integer::sum);
        if (remaining <= 0) {
            owned.remove(itemId);
        }
        // Reflect the new count in the open shop menu (menu only).
        Inventory shop = event.getInventory();
        if (shop != null && SHOP_TITLE.equals(event.getView().getTitle())) {
            updateShopTemplate(shop, itemId, Math.max(0, remaining), player);
        }
    }

    /**
     * Re-renders the menu template for itemId to reflect its selected count. If
     * the item is still level-locked for this player, its barrier form is kept.
     */
    private void updateShopTemplate(Inventory shop, String itemId, int remaining, Player player) {
        for (int i = 0; i < shop.getSize(); i++) {
            ItemStack slot = shop.getItem(i);
            if (slot == null || !itemId.equals(BukkitItemStackAdapter.getRpgItemId(slot))) {
                continue;
            }
            Optional<RPGItem> itemOpt = RPGItemRegistry.getInstance().getItem(itemId);
            if (itemOpt.isPresent()) {
                RPGItem item = itemOpt.get();
                if (!item.isUsableAtLevel(levelOf(player))) {
                    shop.setItem(i, BukkitItemStackAdapter.toLockedItemStack(item));
                    return;
                }
                ItemStack rendered = BukkitItemStackAdapter.toItemStack(item, rpgEntityOf(player));
                if (remaining > 0) {
                    rendered.setAmount(remaining);
                    applyGlow(rendered);
                }
                shop.setItem(i, rendered);
            }
            return;
        }
    }

    private void handleDamage(EntityDamageEvent event) {
        event.setCancelled(true);
    }

    private void handleBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    private void handleBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
    }

    /**
     * Helper method to update all online players' XP to reflect countdown progress
     * 
     * @param secondsRemaining Current seconds remaining
     * @param totalSeconds     Total duration in seconds
     */
    protected final void updateCountdownXP(long secondsRemaining, long totalSeconds) {
        if (totalSeconds <= 0)
            return;

        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            // Level shows seconds remaining
            player.setLevel((int) secondsRemaining);

            // XP bar shows progress (remaining / total)
            float progress = (float) secondsRemaining / (float) totalSeconds;
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            player.setExp(progress);
        }
    }

    private void handleDrop(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

}
