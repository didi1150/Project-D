package dev.bukkit.game.states;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import dev.bukkit.scoreboard.BukkitScoreboardService;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.GameState;

public class SelectItemState extends GameState {

    public static String NAME = "SELECTITEM";
    public static long DURATION = 20 * 60L;
    private static long lockThreshold = 10;
    private final static int minPlayers = 5;

    private BukkitScoreboardService scoreboardService;

    public SelectItemState(EventBusInterface eventBus, BukkitScoreboardService scoreboardService) {
        super(NAME, DURATION, eventBus, false);
        this.scoreboardService = scoreboardService;
    }

    @Override
    protected void onStart() {
        scoreboardService.initScoreboard();
    }

    @Override
    protected void onStop() {
        updateCountdownXP(0, DURATION / 20);
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
        EventAction<BlockBreakEvent> blockBreakAction = new EventAction<BlockBreakEvent>(this::handleBlockBreak,
                BlockBreakEvent.class);
        EventAction<BlockPlaceEvent> blockPlaceAction = new EventAction<BlockPlaceEvent>(this::handleBlockPlace,
                BlockPlaceEvent.class);
        addSubscriber(clickAction);
        addSubscriber(closeAction);
        addSubscriber(quitAction);
        addSubscriber(moveAction);
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
            return;
        }
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
