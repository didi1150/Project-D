package dev.bukkit.ability.behavior;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import dev.bukkit.DMain;

/**
 * Small scheduling helper shared by item behaviors. Item swaps report stale
 * main-hand contents while {@code PlayerItemHeldEvent}/{@code
 * PlayerSwapHandItemsEvent} handlers run, so any behavior work that validates
 * the held item (HUD text displays above all) must run one tick later when
 * the swap has actually been applied.
 */
public final class BehaviorScheduler {

    private BehaviorScheduler() {}

    /** Run on the next server tick; falls back to immediate execution when the plugin is unavailable. */
    public static void runNextTick(Runnable task) {
        Plugin plugin = DMain.getInstance();
        if (plugin != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else {
            task.run();
        }
    }
}
