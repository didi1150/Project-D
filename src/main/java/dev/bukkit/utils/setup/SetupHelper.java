package dev.bukkit.utils.setup;

import dev.bukkit.command.CommandManager;
import dev.bukkit.utils.BukkitMessageSender;
import dev.core.event.EventBusInterface;
import dev.core.utils.MessageComponent;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class SetupHelper {

    protected final List<UUID> playersInMode;
    private final String modeName;

    public SetupHelper(String modeName) {
        this.modeName = modeName;
        playersInMode = new LinkedList<>();

        SetupManager.getInstance().addSetupHelper(this);
    }

    public boolean isPlayerInMode(Player player, BukkitMessageSender ms, boolean sendErrorMessage) {
        if (!playersInMode.contains(player.getUniqueId())) {
            if (sendErrorMessage)
                ms.sendMessage(player, MessageComponent.of(ChatColor.RED + "You can't use this command without being in %s!", modeName));
            return false;
        }
        return true;
    }

    public boolean setPlayerInMode(BukkitMessageSender ms, Player player) {
        if (SetupManager.getInstance().addPlayer(player)) {
            if (playersInMode.contains(player.getUniqueId()))
                return false;
            playersInMode.add(player.getUniqueId());
            giveSetupItemsToPlayer(ms, player);
            return true;
        } else {
            ms.sendMessage(player, MessageComponent.of(ChatColor.RED + "You can't enter %s while being already in another mode!", modeName));
            return false;
        }
    }

    public boolean removePlayerFromMode(Player player) {
        if (SetupManager.getInstance().removePlayer(player)) {
            if (playersInMode.contains(player.getUniqueId())) {
                removeSetupItemsFromPlayer(player);
            }
            return playersInMode.remove(player.getUniqueId());
        }
        return false;
    }

    public void cleanUp(Server server) {
        for (UUID uuid : playersInMode) {
            Player player = server.getPlayer(uuid);
            cleanUp(server, uuid, player);
        }
        playersInMode.clear();
    }

    public abstract void giveSetupItemsToPlayer(BukkitMessageSender ms, Player player);

    public abstract void removeSetupItemsFromPlayer(Player player);

    protected abstract void cleanUp(Server server, UUID uuid, @Nullable Player player);

    public abstract void registerCommand(CommandManager cm, BukkitMessageSender ms);

    public abstract void registerEvents(EventBusInterface eventBus, BukkitMessageSender ms);

}
