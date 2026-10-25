package dev.bukkit.entity.boss;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class BossBarController {

    private final String baseTitle;
    private String title;
    private float progress = 1.0f;

    private final BossBar bossBar;

    public BossBarController(String title) {
        this.baseTitle = title;
        this.title = title;
        this.bossBar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID);
    }

    public void setVisibleToPlayers(Iterable<? extends Player> players) {
        for (Player player : players) {
            if (player.isOnline()) {
                bossBar.addPlayer(player);
            }
        }
    }

    public void updateProgress(float progress) {
        this.title = baseTitle;
        apply(progress);
    }

    public void update(String title, float progress) {
        this.title = title;
        apply(progress);
    }

    private void apply(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
        bossBar.setTitle(title);
        bossBar.setProgress(this.progress);
    }

    public void remove() {
        bossBar.removeAll();
    }

    /**
     * Hides the vanilla boss bar of an entity that has one natively (Wither,
     * Ender Dragon), so it does not double up with our {@link BossBarController}.
     */
    public static void hideVanillaBossBar(Entity entity) {
        if (entity instanceof Boss boss) {
            BossBar bar = boss.getBossBar();
            if (bar != null) {
                bar.setVisible(false);
            }
        }
    }

    /** Restores the vanilla boss bar previously hidden by {@link #hideVanillaBossBar}. */
    public static void showVanillaBossBar(Entity entity) {
        if (entity instanceof Boss boss) {
            BossBar bar = boss.getBossBar();
            if (bar != null) {
                bar.setVisible(true);
            }
        }
    }
}
