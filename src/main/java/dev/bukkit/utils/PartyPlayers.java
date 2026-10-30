package dev.bukkit.utils;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import dev.core.entity.EntityManager;

/** Party resolution helpers for the 5-player co-op run. */
public final class PartyPlayers {

    private PartyPlayers() {
    }

    /** Living, non-spectator players in the anchor player's world. */
    public static List<Player> livingWith(Player anchor) {
        if (anchor == null) {
            return List.of();
        }
        return livingIn(anchor.getWorld());
    }

    /** Living, non-spectator players in the given world. */
    public static List<Player> livingIn(World world) {
        if (world == null) {
            return List.of();
        }
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(world)) {
                continue;
            }
            if (!player.isOnline() || player.isDead() || !player.isValid()) {
                continue;
            }
            if (EntityManager.getInstance().isGhost(player.getUniqueId())) {
                continue;
            }
            players.add(player);
        }
        return players;
    }
}
