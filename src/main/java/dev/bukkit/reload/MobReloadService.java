package dev.bukkit.reload;

import java.util.Map;

import org.bukkit.Bukkit;

import dev.bukkit.storage.BukkitConfigManager;
import dev.core.entity.mob.MobDefinition;
import dev.core.entity.mob.MobDefinitionLoader;
import dev.core.entity.mob.MobDefinitionRegistry;
import dev.core.storage.config.ConfigProvider;

/**
 * Live reload for {@code dungeon-mobs.yml}.
 * Mirrors {@link BossReloadService} – parse before clear so a failure leaves
 * the previous registry intact. Running {@link dev.bukkit.entity.MobRPGEntity}
 * instances keep their captured {@code MobDefinition} snapshot, so reload only
 * affects next spawns (debug or dungeon / boss minions).
 */
public final class MobReloadService {

    private MobReloadService() {
    }

    public static ReloadResult reload(BukkitConfigManager configManager) {
        long start = System.currentTimeMillis();
        int oldSize = MobDefinitionRegistry.getInstance().size();

        ConfigProvider reloaded = configManager.reloadProvider("dungeon-mobs.yml");

        Map<String, MobDefinition> loaded;
        try {
            loaded = MobDefinitionLoader.loadAll(reloaded);
        } catch (Exception e) {
            String msg = "Failed to load dungeon-mobs: " + e.getMessage();
            Bukkit.getLogger().warning(msg);
            return ReloadResult.failure(msg, e);
        }

        // Apply atomically – replaceAll keeps synchronized contract
        MobDefinitionRegistry.getInstance().replaceAll(loaded.values());

        long elapsed = System.currentTimeMillis() - start;
        String summary = String.format("mobs %d->%d definitions in %dms", oldSize, loaded.size(), elapsed);
        Bukkit.getConsoleSender().sendMessage("Reloaded " + loaded.size() + " dungeon mob definition(s).");
        return ReloadResult.success(summary, loaded.size());
    }

    public static final class ReloadResult {
        public final boolean success;
        public final String message;
        public final Throwable error;
        public final int mobs;

        private ReloadResult(boolean success, String message, Throwable error, int mobs) {
            this.success = success;
            this.message = message;
            this.error = error;
            this.mobs = mobs;
        }

        public static ReloadResult success(String msg, int mobs) {
            return new ReloadResult(true, msg, null, mobs);
        }

        public static ReloadResult failure(String msg, Throwable e) {
            return new ReloadResult(false, msg, e, 0);
        }
    }
}
