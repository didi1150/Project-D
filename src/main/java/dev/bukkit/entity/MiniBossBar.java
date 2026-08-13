package dev.bukkit.entity;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;

import dev.bukkit.DMain;
import dev.bukkit.entity.boss.BossBarController;

/**
 * Boss-bar health display for mini-bosses (vanilla LivingEntities). Reuses the
 * real boss's {@link BossBarController}, tracking the entity's health and
 * re-syncing viewers every few ticks so late joiners see the bar too. Hides any
 * vanilla boss bar the entity has natively (e.g. a Wither), and restores it on
 * cleanup. The repeating task cancels itself when the mini-boss dies or the
 * dungeon despawns it.
 */
public final class MiniBossBar {

    private static final long UPDATE_INTERVAL_TICKS = 5L;
    private BukkitTask task;

    private MiniBossBar(String title, LivingEntity entity) {
        String translated = ChatColor.translateAlternateColorCodes('&', title);
        BossBarController controller = new BossBarController(translated);
        BossBarController.hideVanillaBossBar(entity);

        task = Bukkit.getScheduler().runTaskTimer(DMain.getInstance(), () -> {
            if (!entity.isValid() || entity.isDead()) {
                BossBarController.showVanillaBossBar(entity);
                controller.remove();
                task.cancel();
                return;
            }
            controller.setVisibleToPlayers(Bukkit.getOnlinePlayers());
            double max = entity.getAttribute(Attribute.MAX_HEALTH).getValue();
            double pct = max <= 0 ? 0 : entity.getHealth() / max;
            controller.updateProgress((float) Math.max(0.0, pct));
        }, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
    }

    public static void track(String title, LivingEntity entity) {
        new MiniBossBar(title, entity);
    }
}