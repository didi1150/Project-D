package dev.bukkit.game.states;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import dev.bukkit.game.coords.PointToLocation;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.GameState;
import dev.core.game.GameStateResult;
import dev.core.game.ScheduledTask;
import dev.core.game.coords.ViewPoint3D;

public class PreLobbyState extends GameState {

    public static String NAME = "PRELOBBY";

    private enum Mode {
        WAITING, COUNTDOWN
    }

    private final int minPlayers;
    private final int maxCountdown;
    private Mode currentMode;
    private int countdown;
    private ScheduledTask broadcastTask;
    private ScheduledTask countdownTask;
    private Location spawnLocation;

    public PreLobbyState(ViewPoint3D spawnLocation, int minPlayers, int countDownSeconds, EventBusInterface eventBus) {
        super(NAME, -1, eventBus);
        this.spawnLocation = PointToLocation.viewToLoc(spawnLocation);
        this.minPlayers = minPlayers;
        this.maxCountdown = countDownSeconds;
    }

    @Override
    protected void onStart() {
        enterWaitingMode();
    }

    @Override
    protected void onStop() {
        cancelAllTasks();

        switch (currentMode) {
        case WAITING:
            Bukkit.broadcastMessage("§aEnough players found!");
            break;
        case COUNTDOWN:
            Bukkit.broadcastMessage("§aStarting game...");
            break;
        }
    }

    @Override
    protected void registerSubscribers() {
        EventAction<PlayerJoinEvent> joinAction = new EventAction<>(this::handleJoin, PlayerJoinEvent.class);

        EventAction<PlayerQuitEvent> quitAction = new EventAction<>(this::handleQuit, PlayerQuitEvent.class);

        EventAction<EntityDamageEvent> damageAction = new EventAction<EntityDamageEvent>(this::handleDamage,
                EntityDamageEvent.class);
        EventAction<BlockBreakEvent> blockBreakAction = new EventAction<BlockBreakEvent>(this::handleBlockBreak,
                BlockBreakEvent.class);
        EventAction<BlockPlaceEvent> blockPlaceAction = new EventAction<BlockPlaceEvent>(this::handleBlockPlace,
                BlockPlaceEvent.class);

        addSubscriber(quitAction);
        addSubscriber(joinAction);
        addSubscriber(blockPlaceAction);
        addSubscriber(blockBreakAction);
        addSubscriber(damageAction);
    }

    private void handleQuit(PlayerQuitEvent event) {
        int playerCount = Bukkit.getOnlinePlayers().size() - 1;

        switch (currentMode) {
        case WAITING:
            // Update waiting display
            scheduler.runTaskLater(() -> updateWaitingModeXP(), 1L);
            break;

        case COUNTDOWN:
            if (playerCount < minPlayers) {
                scheduler.runTaskLater(() -> enterWaitingMode(), 1L);
            }
            break;
        }
    }

    private void handleJoin(PlayerJoinEvent event) {
        int playerCount = Bukkit.getOnlinePlayers().size();
        if (playerCount > minPlayers) {
            scheduler.runTaskLater(() -> event.getPlayer().kickPlayer("Server is full"), 1);
            return;
        }
        scheduler.runTaskLater(() -> {
            event.getPlayer().teleport(spawnLocation);
            event.getPlayer().setGameMode(GameMode.ADVENTURE);
            event.getPlayer().setHealth(event.getPlayer().getAttribute(Attribute.MAX_HEALTH).getValue());
        }, 1);
        switch (currentMode) {
        case WAITING:
            if (playerCount == minPlayers) {
                enterCountdownMode();
            } else {
                updateWaitingModeXP();
            }
            break;

        case COUNTDOWN:
            if (playerCount < minPlayers) {
                enterWaitingMode();
            } else {
                updateCountdownModeXP();
            }
            break;
        }
    }

    private void enterWaitingMode() {
        if (currentMode == Mode.WAITING) {
            return;
        }

        currentMode = Mode.WAITING;
        cancelCountdownTask();

        updateWaitingModeXP();

        broadcastTask = scheduler.runTaskTimer(() -> {
            if (!isActive() || currentMode != Mode.WAITING) {
                return;
            }

            Bukkit.broadcastMessage("§eWaiting for players... (" + getCurrentPlayerCount() + "/" + minPlayers + ")");
            updateWaitingModeXP();
        }, 20 * 15L, 20 * 15L); // Every 15 seconds
    }

    private int getCurrentPlayerCount() {
        return Bukkit.getOnlinePlayers().size();
    }

    private void enterCountdownMode() {
        if (currentMode == Mode.COUNTDOWN) {
            return;
        }

        currentMode = Mode.COUNTDOWN;
        cancelWaitingTask();
        countdown = maxCountdown;

        Bukkit.broadcastMessage("§aStarting countdown...");

        countdownTask = scheduler.runTaskTimer(() -> {
            if (!isActive() || currentMode != Mode.COUNTDOWN) {
                return;
            }

            if (countdown > 0) {
                // Update XP for all players
                updateCountdownModeXP();

                Bukkit.broadcastMessage("§e" + countdown + "...");
                countdown--;
            } else {
                complete(GameStateResult.COMPLETE);
            }
        }, 0L, 20L);
    }

    private void updateWaitingModeXP() {
        int currentPlayers = getCurrentPlayerCount();

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Level shows current player count
            player.setLevel(currentPlayers);

            // XP bar shows progress toward minimum
            float progress = Math.min(1.0f, (float) currentPlayers / (float) minPlayers);
            player.setExp(progress);
        }
    }

    private void updateCountdownModeXP() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Level shows countdown seconds
            player.setLevel(countdown);

            // XP bar shows remaining time percentage
            float progress = (float) countdown / (float) maxCountdown;
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            player.setExp(Math.max(0.0f, Math.min(1.0f, progress)));
        }
    }

    private void cancelCountdownTask() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void cancelWaitingTask() {
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }
    }

    private void cancelAllTasks() {
        cancelWaitingTask();
    }

    public Mode getMode() {
        return currentMode;
    }

    public int getCountdown() {
        return countdown;
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
}