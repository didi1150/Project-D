package dev.bukkit.reload;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;

import dev.bukkit.DMain;
import dev.bukkit.entity.boss.BukkitBossStageTypeRegistry;
import dev.bukkit.entity.boss.BukkitBossStrategyRegistry;
import dev.bukkit.storage.BukkitConfigManager;
import dev.core.entity.boss.BossDefinition;
import dev.core.entity.boss.BossDefinitionLoader;
import dev.core.entity.boss.BossDefinitionRegistry;
import dev.core.entity.boss.FloorData;
import dev.core.entity.boss.FloorDataLoader;
import dev.core.entity.boss.FloorDataRegistry;
import dev.core.storage.config.ConfigProvider;

/**
 * Live reload for {@code bosses.yml} (both {@code bosses} and top-level
 * {@code floor-data}). Follows the pattern of
 * {@link ItemsAbilitiesReloadService} and {@code /d hud reload}.
 *
 * <p>
 * Both sections are parsed before any registry is cleared, so a failure leaves
 * the previous state intact.
 * </p>
 */
public final class BossReloadService {

    private BossReloadService() {
    }

    public static ReloadResult reload(BukkitConfigManager configManager) {
        long start = System.currentTimeMillis();

        // Snapshot old counts for reporting
        int oldBosses = BossDefinitionRegistry.getInstance().size();
        int oldFloors = FloorDataRegistry.getInstance().size();

        ConfigProvider reloaded = configManager.reloadProvider("bosses.yml");

        List<BossDefinition> loadedBosses;
        Map<Integer, FloorData> loadedFloorDatas;
        try {
            loadedBosses = BossDefinitionLoader.loadAll(reloaded, new BukkitBossStageTypeRegistry(),
                    new BukkitBossStrategyRegistry());
        } catch (Exception e) {
            String msg = "Failed to load bosses: " + e.getMessage();
            Bukkit.getLogger().warning(msg);
            return ReloadResult.failure(msg, e);
        }

        try {
            loadedFloorDatas = FloorDataLoader.loadAll(reloaded);
        } catch (Exception e) {
            String msg = "Failed to load floor-data: " + e.getMessage();
            Bukkit.getLogger().warning(msg);
            return ReloadResult.failure(msg, e);
        }

        // Optional guard: could block if BossState active; for now allow but warn via
        // caller
        // Apply atomically
        BossDefinitionRegistry.getInstance().clear();
        BossDefinitionRegistry.getInstance().registerAll(loadedBosses);

        FloorDataRegistry.getInstance().replaceAll(loadedFloorDatas);

        // If a boss is currently active, its FloorData snapshot is already captured in
        // RPGBossEntity at spawn time, so this reload only affects next encounter.
        long elapsed = System.currentTimeMillis() - start;
        String summary = String.format("bosses %d->%d definitions, floor-data %d->%d floors in %dms", oldBosses,
                loadedBosses.size(), oldFloors, loadedFloorDatas.size(), elapsed);

        // Also log counts via DMain console style
        Bukkit.getConsoleSender().sendMessage("Reloaded " + loadedBosses.size() + " boss definition(s) and "
                + loadedFloorDatas.size() + " floor-data entries.");

        return ReloadResult.success(summary, loadedBosses.size(), loadedFloorDatas.size());
    }

    public static final class ReloadResult {
        public final boolean success;
        public final String message;
        public final Throwable error;
        public final int bosses;
        public final int floors;

        private ReloadResult(boolean success, String message, Throwable error, int bosses, int floors) {
            this.success = success;
            this.message = message;
            this.error = error;
            this.bosses = bosses;
            this.floors = floors;
        }

        public static ReloadResult success(String msg, int bosses, int floors) {
            return new ReloadResult(true, msg, null, bosses, floors);
        }

        public static ReloadResult failure(String msg, Throwable e) {
            return new ReloadResult(false, msg, e, 0, 0);
        }
    }
}
