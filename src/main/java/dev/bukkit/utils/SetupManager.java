package dev.bukkit.utils;

import dev.bukkit.command.CommandManager;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class SetupManager {

    private static final SetupManager INSTANCE = new SetupManager();

    public static SetupManager getInstance() {
        return INSTANCE;
    }

    private final List<UUID> playersInAnyMode;
    private final List<SetupHelper> setupHelpers;

    private SetupManager() {
        playersInAnyMode = new LinkedList<>();
        setupHelpers = new LinkedList<>();
    }

    public boolean addPlayer(Player player) {
        if (playersInAnyMode.contains(player.getUniqueId()))
            return false;
        playersInAnyMode.add(player.getUniqueId());
        return true;
    }

    public boolean removePlayer(Player player) {
        return playersInAnyMode.remove(player.getUniqueId());
    }

    public void addSetupHelper(SetupHelper setupHelper) {
        setupHelpers.add(setupHelper);
    }

    public void registerSetupHelpers(CommandManager cm, EventBusInterface eventBus, BukkitMessageSender ms) {
        for (SetupHelper setupHelper : setupHelpers) {
            setupHelper.registerCommand(cm, ms);
            setupHelper.registerEvents(eventBus, ms);
        }
        EventAction<PlayerQuitEvent> playerLeave = new EventAction<>(event -> {
            Player player = event.getPlayer();
            if (playersInAnyMode.contains(player.getUniqueId())) {
                for (SetupHelper setupHelper : setupHelpers) {
                    if (setupHelper.isPlayerInMode(player, ms, false)) {
                        setupHelper.removePlayerFromMode(player);
                        break;
                    }
                }
            }
        }, PlayerQuitEvent.class);
        eventBus.subscribe(playerLeave);
    }

    public void cleanUpSetupHelpers(Server server) {
        for (SetupHelper setupHelper : setupHelpers) {
            setupHelper.cleanUp(server);
        }
    }
}
