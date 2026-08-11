package dev.bukkit.entity.boss;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

public class BossBarController {

    private final String title;
    private final Set<UUID> viewers = new HashSet<>();
    private float progress = 1.0f;

    private final BossBar bossBar;

    public BossBarController(String title) {
        this.title = title;
        this.bossBar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID);
    }

    public void addViewer(Player player) {
        viewers.add(player.getUniqueId());
        sendBar(player, true);
    }

    public void removeViewer(Player player) {
        viewers.remove(player.getUniqueId());
        sendBar(player, false);
    }

    public void setVisibleToPlayers(Iterable<? extends Player> players) {
        for (Player player : players) {
            addViewer(player);
        }
    }

    public void updateProgress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
        for (UUID viewer : new HashSet<>(viewers)) {
            Player player = Bukkit.getPlayer(viewer);
            if (player != null) {
                sendBar(player, true);
            }
        }
    }

    public void remove() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            sendBar(player, false);
        });
    }

    private void sendBar(Player player, boolean visible) {
        if (!player.isOnline()) {
            bossBar.removePlayer(player);
            viewers.remove(player.getUniqueId());
            return;
        }

        if (visible) {
            bossBar.setTitle(title);
            bossBar.setProgress(progress);
            bossBar.addPlayer(player);
        } else {
            bossBar.removePlayer(player);
            viewers.remove(player.getUniqueId());
        }
    }

    public String getTitle() {
        return title;
    }

    public float getProgress() {
        return progress;
    }
}
