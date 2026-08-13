package dev.bukkit.game.states;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.inventory.ItemStack;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.event.impl.TickEvent;
import dev.core.game.GameState;
import dev.core.game.ScheduledTask;
import dev.core.item.RPGItem;
import dev.core.item.loader.RPGItemRegistry;

public class SelectItemState extends GameState {

    public static String NAME = "SELECTITEM";
    public static long DURATION = 20 * 60L;
    private static long lockThreshold = 10;
    private final static int minPlayers = 5;
    private static final String SHOP_TITLE = "Select an Item";

    private long lastMillis;
    private ScheduledTask scheduledTask;

    public SelectItemState(EventBusInterface eventBus) {
        super(NAME, DURATION, eventBus);
    }

    @Override
    protected void onStart() {
        scheduledTask = scheduler.runTaskTimer(() -> {
            float tickDelta = (System.currentTimeMillis() - lastMillis) / 1000f * 20f;
            lastMillis = System.currentTimeMillis();
            eventBus.sendEvent(new TickEvent(tickDelta));
        }, 0, 1);

        // Open the class-filtered item shop for everyone, once the state settles.
        scheduler.runTaskLater(() -> Bukkit.getOnlinePlayers().forEach(this::openShop), 5L);
    }

    /**
     * Opens the item draft chest for a player, listing only player-usable items
     * (never {@code mob-only}) that match the player's active RPG class.
     */
    private void openShop(Player player) {
        Inventory shop = Bukkit.createInventory(null, 54, SHOP_TITLE);
        RPGClassType playerClass = activeClassOf(player);
        int slot = 0;
        for (RPGItem item : RPGItemRegistry.getInstance().allItems().values()) {
            if (!item.isAllowedForClass(playerClass)) {
                continue;
            }
            if (slot >= 54) {
                break;
            }
            shop.setItem(slot++, BukkitItemStackAdapter.toItemStack(item));
        }
        player.openInventory(shop);
    }

    private RPGClassType activeClassOf(Player player) {
        Optional<RPGEntity> entity = EntityManager.getInstance().getEntity(player.getUniqueId());
        if (entity.isPresent() && entity.get() instanceof BukkitPlayerEntity playerEntity) {
            return playerEntity.getPlayerProgression().getActiveClass();
        }
        return RPGClassType.NONE;
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
        event.setCancelled(true);
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
            player.getInventory().addItem(stack.clone());
        });
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
